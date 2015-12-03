# Idiomatic Scala AWS Library #

![awslib_scala Logo](https://raw.githubusercontent.com/mslinn/awslib_scala/master/images/awsLib_76x78.png)
This project is sponsored by [Micronautics Research Corporation](http://www.micronauticsresearch.com/),
the company behind [Cadenza](http://www.micronauticsresearch.com/products/cadenza/index.html) and [ScalaCourses.com](http://www.scalacourses.com).

## Idomatic Scala ##
This library provides a functional interface to the [AWS Java library](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/index.html).
The exposed API is much simpler to use than Amazon's Java API, however you can mix calls to this library with calls to the underlying AWS Java library.

Container classes such as `CloudFront`, `IAM`, `S3`, and `SNS` have been defined that encapsulate top-level functionality.
The container classes are defined using composition instead of inheritance.

This library uses implicit values to simplify usage.
Some AWS Java classes have been enhanced using implicit classes so they appear to have extra capability.
Enhanced AWS classes include CloudFront's `DistributionSummary`, S3's `Bucket` and IAM's `User`.
Most methods employ typed parameters so accidental mixing up of arguments cannot happen.
Programmers using this library are encouraged to use named parameters for the few remaining untyped parameters.

## Building and Running ##

 1. Java 8 is required.
 2. You need an AWS account.
[Separate AWS accounts](http://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/consolidated-billing.html) for development and production are recommended.
 3. Your AWS keys must either be defined in environment variables called `AWS_ACCESS_KEY` and `AWS_SECRET_KEY`
or you must have configured [AWS CLI](http://aws.amazon.com/cli/) with your AWS authentication credentials.
If environment variables and the AWS CLI configuration file are all available, the environment variables have precedence.
 4. Type the following into a bash console:
````
git clone https://github.com/mslinn/awslib_scala.git
cd awslib_scala
sbt test
````

## Installation ##
Add this to your project's `build.sbt`:

    resolvers += "micronautics/scala on bintray" at "http://dl.bintray.com/micronautics/scala"

    libraryDependencies += "com.micronautics" %% "awslib_scala" % "1.1.4" withSources()

## Sample Code ##
See the unit tests for examples of how to use this library.
