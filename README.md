# Idiomatic Scala AWS Library

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://travis-ci.org/mslinn/awslib_scala.svg?branch=master)](https://travis-ci.org/mslinn/awslib_scala)
[ ![Download](https://api.bintray.com/packages/micronautics/scala/awslib_scala/images/download.svg) ](https://bintray.com/micronautics/scala/awslib_scala/_latestVersion)

![awslib_scala Logo](https://raw.githubusercontent.com/mslinn/awslib_scala/master/images/awsLib_76x78.png)
This project is sponsored by [Micronautics Research Corporation](https://www.micronauticsresearch.com/),
the company behind [Cadenza](https://www.micronauticsresearch.com/products/cadenza/index.html) and
[ScalaCourses.com](https://www.scalacourses.com).

## Idiomatic Scala
This library provides a functional interface to the [AWS Java library](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/index.html).
The exposed API is much simpler to use than Amazon's Java API,
however you can mix calls to this library with calls to the underlying AWS Java library.

Container classes such as `CloudFront`, `IAM`, `Polly`, `Rekoginzer`, `S3`, `SNS` and `SQS` are defined that
encapsulate top-level functionality.
The container classes are defined using composition instead of inheritance.

This library uses implicit values to simplify usage.
Some AWS Java classes have been enhanced using implicit classes so they appear to have extra capability.
Enhanced AWS classes include CloudFront's `DistributionSummary`, S3's `Bucket` and IAM's `User`.
Most methods employ typed parameters so accidental mixing up of arguments cannot happen.
Programmers using this library are encouraged to use named parameters for the few remaining untyped parameters.

## Building and Running

 1. Java 8 is required.
 2. You need an AWS account.
[Separate AWS accounts](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/consolidated-billing.html)
for development and production are recommended.
 3. Your AWS keys must either be defined in environment variables called `AWS_ACCESS_KEY` and `AWS_SECRET_KEY`
or you must have configured [AWS CLI](https://aws.amazon.com/cli/) with your AWS authentication credentials.
If environment variables and the AWS CLI configuration file are all available, the environment variables have precedence.
 4. Type the following into a bash console:
````
git clone https://github.com/mslinn/awslib_scala.git
cd awslib_scala
sbt test
````

## Installation
Add this to your project's `build.sbt`:

    resolvers += "micronautics/scala on bintray" at "https://dl.bintray.com/micronautics/scala"

    libraryDependencies += "com.micronautics" %% "awslib_scala" % "1.1.12" withSources()

## Sample Code
See the unit tests for examples of how to use this library.

## Scaladoc
[Here](http://mslinn.github.io/awslib_scala/latest/api/com/micronautics/aws/index.html)
