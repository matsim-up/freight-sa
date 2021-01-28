[![Travis Build Status](https://travis-ci.com/matsim-up/freight-sa.svg?branch=master)](https://travis-ci.com/matsim-up/freight-sa)
[![Packagecloud Repository](https://img.shields.io/badge/java-packagecloud.io-844fec.svg)](https://packagecloud.io/matsim-up/freight-sa/)


# freight-sa
Code that deals with the extraction, modelling and analyses of commercial vehicle activity chains. This project, in turn, depends on [matsim-up](https://github.com/matsim-up/matsim-up). Continuous integration (CI) is done on [Travis-CI](https://travis-ci.com/matsim-up/freight-sa) and (snapshot) jars are deployed to [packagecloud](https://packagecloud.io/matsim-up/freight-sa).

## Usage

To use `freight-sa` as a dependency in an external maven project, update the external project's `pom.xml` file by adding the [PackageCloud](https://packagecloud.io/matsim-up/matsim-up) repository

```
<repositories>
	<repository>
		<id>matsim-up-freight-sa</id>
		<url>https://packagecloud.io/matsim-up/freight-sa/maven2</url>
	</repository>
</repositories>
```

and the depency on `freight-sa`

```
<dependencies>
	<dependency>
  		<groupId>org.matsim.up.freight</groupId>
  		<artifactId>freight-sa</artifactId>
  		<version>13.0-2021w03-SNAPSHOT</version>
	</dependency>
</dependencies>
```
where the version is indicative of the specific [`matsim`](https://github.com/matsim-org/matsim-libs) 
and [matsim-up`](https://github.com/matsim-up/matsim-up) versions that this repository is compatible with. 
