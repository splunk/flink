#!/bin/bash

SPLUNK_MAJOR_VERSION="1.13"

git clone https://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@github.com/splunk/flink.git
git config --global user.email "srampally@splunk.com"
git config --global user.name "Srikar Rampally"
git config --global --add url."git@github.com:".insteadOf "https://github.com/"

cd flink
git pull
git branch

echo "cloned splunk/flink repo"
echo "-------------------------------------------"

# Add upstream which points to Flink repo and update splunk repo's master
git remote add upstream https://github.com/apache/flink.git

git fetch upstream
git pull upstream master

echo "test push ---------------------------------"
git push origin master

tags=`git ls-remote --tags https://github.com/apache/flink.git | grep release | grep -Ev 'rc|{}'`

release_tags=()
for tag in $tags; do

    if [[ $tag == *"release"* ]]; then

        value=`echo $tag | awk -F/ '{print $NF}' | awk -F- '{print $2}' | grep $SPLUNK_MAJOR_VERSION`
        release_tags+=($value)
    else
        continue
    fi
done

latest_release_tag_version=${release_tags[${#release_tags[@]} - 1]}
latest_upstream_release_tag="release-$latest_release_tag_version"
echo "Latest release tag: $latest_upstream_release_tag"
echo "-------------------------------------------"

splunk_branches=`git branch -r`

splunk_release_branches=()
for branch in $splunk_branches; do

    if [[ $branch == *"release"* ]]; then
        value=`echo $branch | awk -F- '{print $2}' | grep $SPLUNK_MAJOR_VERSION`
        splunk_release_tags+=($value)
    else
        continue
    fi
done

current_splunk_release_tag_version=${splunk_release_tags[${#splunk_release_tags[@]} - 1]}
current_splunk_release_tag="release-$current_splunk_release_tag_version-splunk"
echo "Current Splunk release tag: $current_splunk_release_tag"

latest_splunk_release_tag="release-$latest_release_tag_version-splunk"
echo "Latest splunk release tag: $latest_splunk_release_tag"

old_upstream_release_tag="release-$current_splunk_release_tag_version"
echo "Old Splunk equivalent upstream release tag: $old_upstream_release_tag"
echo "-------------------------------------------"

if [[ $latest_release_tag_version = $current_splunk_release_tag_version ]]; then
    echo "${green}Latest minor release tag matches with the current splunk release tag${reset}"
    latest_splunk_release_tag="release-$latest_release_tag-splunk"
    echo $latest_splunk_release_tag
    echo "-------------------------------------------"
else
    echo "Latest minor release tag doesn't match with the current splunk release tag"
    echo "-------------------------------------------"
    #creates a splunk specific release branch
    git fetch upstream --tags
    git checkout -b $latest_splunk_release_tag $latest_upstream_release_tag

    # origin should be pointing to git@github.com:splunk/flink.git
#    git push origin $latest_splunk_release_tag

    # finds the rebased from the release tag version
    base=`git log origin/$current_splunk_release_tag --grep="Commit for release $current_splunk_release_tag_version" --format="%H"`
    echo "Most recent common ancestor commit: $base"
    echo "-------------------------------------------"

    # gets the list of commits that have been added after the branch has been cutover
    diff=`git rev-list --ancestry-path $base..origin/$current_splunk_release_tag --reverse`
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

#    git push origin $latest_splunk_release_tag

    echo "Successfully cherry-picked all commits from $current_splunk_release_tag to $latest_splunk_release_tag"
fi
