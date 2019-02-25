# GitHub wiki disabler

Script to automatically disable wikis on all repositories in an organisation.

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
