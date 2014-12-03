# AWS Library for Scala #

This project is sponsored by [Micronautics Research Corporation](http://www.micronauticsresearch.com/)

## Building ##

 1. Java 7 or later is required.
 2. Type the following into a bash console:
````
git clone https://github.com/mslinn/AwsS3.git
cd AwsS3
sbt publish-local # only for testing
````
## Publishing ##

    bin/publish

## Installation ##
Add this to your project's `build.sbt`:

    resolvers += "Micronautics releases" at "http://www.mavenrepo.s3.amazonaws.com/releases"
    
    libraryDependencies += "com.micronautics" % "awss3" % "0.2.0" withSources()

## Notes ##
When web site access is enabled, AWS content is accessed by paths constructed by concatenating the URL, a slash (/),
and the keyed data.
The keys must therefore consist of relative paths (relative directory name followed by a file name),
and must not start with a leading slash.
This program stores each file name (referred to by AWS as a key) without a leading slash.
For example, assuming that the default file name is `index.html`,
`http://domain.com` and `http://domain.com/` are translated to `http://domain.com/index.html`.

As another example, the key for a file in a directory called `{WEBROOT}/blah/ick/yuck.html` is defined as `blah/ick/yuck.html`.

For each directory, AWS creates a file of the same name, with the suffix `_$folder$`.
If one of those files are deleted, the associated directory becomes unreachable. Don't mess with them.
These hidden files are ignored by this program; users never see them because they are for AWS S3 internal use only.
