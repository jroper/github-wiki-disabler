# GitHub wiki disabler

Script to automatically disable wikis on all repositories in an organisation.

This was created because prior to around 2015, any repositories created in GitHub, by default, had the wiki enabled, and allowed anyone to edit the wiki, even if they didn't have push access to the repository. This opens up somewhat of an exploit opportunity, as it allows a malicious user to post malicious information (eg, links to malicious versions of the project) to the wiki, and exploit the fact that many people might see the wiki as a trusted source of information.

Unfortunately, GitHub doesn't expose the anyone can edit configuration option of wikis in the GitHub API, so there's no straight forward way of auditing and updating this. However, in all the organisations I'm part of, the wiki is almost never used, so just disabling the wiki altogether achieves the same thing.

## Usage

Create a GitHub application token, and set in the environment variable:

```
export GITHUB_TOKEN=e72e16c7e42f292c6912e7710c838347ae178b4a
```

Now run the project, passing the organisation you want to disable the wikis of as an argument:

```
sbt "run playframework"
```

The script will only disable a wiki if the following is true:

* The wiki is enabled
* The project is not archived (since you can't disable the wiki of an archived project)
* The wiki is either empty, or contains a single Home.md file containing less than 100 bytes

If last condition is not true, then the script will output a link to the wiki at the end of the run so that it can be manually checked to see if it needs the wiki or not.
