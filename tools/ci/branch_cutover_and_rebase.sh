#!/bin/bash

SPLUNK_MAJOR_VERSION="1.13"

git config --global user.email "srikarrampally@gmail.com"
git config --global user.name "srikarrampa"
git clone https://github.com/splunk/flink.git

cd flink
git pull
git branch

echo "cloned splunk/flink repo"
echo "-------------------------------------------"

# Add upstream which points to Flink repo and update splunk repo's master
git remote add upstream https://github.com/apache/flink.git

git fetch upstream
git pull upstream master

echo "Push changes from upstream https://github.com/apache/flink.git to https://github.com/splunk/flink.git master"
url="https://$1@github.com/splunk/flink.git"
push_result=$(git push $url master 2>&1)
if [[ $push_result = *"fatal"* ]]; then
    echo "Push to splunk/flink failed. Please check permissions"
    exit 1
fi

tags=`git ls-remote --tags https://github.com/apache/flink.git | grep release | grep -Ev 'rc|{}'`

release_tags=()
for tag in $tags; do

    if [[ $tag == *"release-$SPLUNK_MAJOR_VERSION"* ]]; then

        value=`echo $tag | awk -F/ '{print $NF}' | awk -F- '{print $2}'`
        release_tags+=($value)

    fi
done

latest_release_tag_version=${release_tags[${#release_tags[@]} - 1]}
latest_upstream_release_tag="release-$latest_release_tag_version"
echo "Latest release tag: $latest_upstream_release_tag"
echo "-------------------------------------------"

splunk_branches=`git ls-remote --heads https://github.com/splunk/flink.git`

splunk_release_branches=()
for branch in $splunk_branches; do

    if [[ $branch == *"release-$SPLUNK_MAJOR_VERSION"* ]]; then
        value=`echo $branch | awk -F- '{print $2}'`
        splunk_release_tags+=($value)
    fi

done

current_splunk_release_branch_version=${splunk_release_tags[${#splunk_release_tags[@]} - 1]}
current_splunk_release_branch="release-$current_splunk_release_branch_version-splunk"
echo "Current Splunk release tag: $current_splunk_release_branch"
echo "-------------------------------------------"

latest_splunk_release_tag="release-$latest_release_tag_version-splunk"
echo "Latest splunk release tag: $latest_splunk_release_tag"
echo "-------------------------------------------"

old_upstream_release_tag="release-$current_splunk_release_branch_version"
echo "Old Splunk equivalent upstream release tag: $old_upstream_release_tag"
echo "-------------------------------------------"

if [[ $latest_release_tag_version = $current_splunk_release_branch_version ]]; then
    echo "${green}Latest minor release tag matches with the current splunk release tag${reset}"
    latest_splunk_release_tag="release-$latest_release_tag_version-splunk"
    echo $latest_splunk_release_tag
    echo "-------------------------------------------"
else
    echo "Latest minor release tag doesn't match with the current splunk release tag"
    echo "-------------------------------------------"
    #creates a splunk specific release branch
    git fetch upstream --tags
    git checkout -b $latest_splunk_release_tag $latest_upstream_release_tag

    # origin should be pointing to git@github.com:splunk/flink.git
#    git push $url $latest_splunk_release_tag



    # finds the rebased from the release tag version
    base=`git log origin/$current_splunk_release_branch --grep="Commit for release $current_splunk_release_branch_version" --format="%H"`
    echo "Most recent common ancestor commit: $base"
    echo "-------------------------------------------"

    # gets the list of commits that have been added after the branch has been cutover
    diff=`git rev-list --ancestry-path $base..origin/$current_splunk_release_branch --reverse`
    echo "List of commits: $diff"
    echo "-------------------------------------------"

    if [[ -z "$diff" ]]; then
      echo "No commits to cherry pick. Please check build logs and script"
      exit 1
    fi

    # cherry pick commits based on requirement
    for commit in $diff; do

        commit_info=`git rev-list --format=%B --max-count=1 $commit`

        echo "$commit_info"

        if [[ $commit_info = *"modify flink versioning"* ]]; then
            continue
        fi

        result=`git cherry-pick $commit`
        echo "$result"
        echo "-------------------------------------------"

        if [ "$result" = *"CONFLICT"* ] || [ "$result" = *"failed"* ]; then
            echo "Conflict occured. Please resolve manually"
            echo "Conflict occurred while cherry-picking for commit-sha: $commit and commit description: $commit_info . Please resolve manually"
            exit 1
        fi
    done

    # modify flink versioning to append splunk-SNAPSHOT
    cd tools
    sh change-version.sh $latest_release_tag_version
    cd ..
    git add .
    git commit -m "modify flink versioning"
    echo "-------------------------------------------"

#    git push $url $latest_splunk_release_tag

    echo "Successfully cherry-picked all commits from $current_splunk_release_branch to $latest_splunk_release_tag"
fi
