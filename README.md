# oci-java-sdk-simple-wrappers

[![License: UPL](https://img.shields.io/badge/license-UPL-green)](https://img.shields.io/badge/license-UPL-green) [![Quality gate](https://sonarcloud.io/api/project_badges/quality_gate?project=oracle-devrel_oci-java-sdk-simple-wrappers)](https://sonarcloud.io/dashboard?id=oracle-devrel_oci-java-sdk-simple-wrappers)

## Introduction
This is a small set of wrappers around the OCI Java SDK.

I created it because while the oci java SDK is very powerfuil it's also not a model I foound intuitive (for me at least) So as I need them I have been creating simple wrappers around the OCI Java SDK to help me do things. Of course you may not find them to be useful, but they help me.

This is by no means a complete set of functionality, it's focused on things that I needed to do, but I suspect over time more capabilities will be added.

## Getting Started
You will need to have setup a OCI tenancy, and created a user within that tenancy. That user should have the appropriate permisions (set via OCI policies) to do the things you want to use - this code **will not** override the OCI security (not least of which is it's impemented in the API, not my code).

To get started you will also user API signing keys and credentials for the user in OCI and a ".oci" directory appropriately configured ([see the OCI documentation on hos to do this](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm#SDK_and_CLI_Configuration_File))

To use the code to manage objects in OCI you will need to :

Create an AuthenticationProcessor (com.oracle.timg.oci.authentication) - this does the security work for you, speciy the OCI configuration file section to use for the connection and optionall the OCI region to work on (by defual it will sue the region in the config file)

From there use the other processor classes as needed, these will all need the AuthenticationProcessor you created as part of their consructor.



### Prerequisites
You will need a development environment with Java (17 is the cersion Im using, earlier versions may not be supported by the OCI Java SDK).

I also use Maven to build this and to manage the dependencies, but if you have other mechanisms feel free.

I use [Lombok](https://projectlombok.org/) to generate logs and the like, this is included in the Maven pom.xml, but your IDE or compile time will need to include the lombok jar file to process the annotations - usually just running the lombok jar file will let you locate the IDE and install it for you.


## Notes/Issues
This is a **very** limited set of OCI services that are being wrapped here, over time I'll probabaly add some more.

## URLs
[The OCI Java SDK is documented here](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm)

## Contributing
This project is open source.  Please submit your contributions by forking this repository and submitting a pull request!  Oracle appreciates any contributions that are made by the open source community.

## License
Copyright (c) 2024 Oracle and/or its affiliates.

Licensed under the Universal Permissive License (UPL), Version 1.0.

See [LICENSE](LICENSE) for more details.

ORACLE AND ITS AFFILIATES DO NOT PROVIDE ANY WARRANTY WHATSOEVER, EXPRESS OR IMPLIED, FOR ANY SOFTWARE, MATERIAL OR CONTENT OF ANY KIND CONTAINED OR PRODUCED WITHIN THIS REPOSITORY, AND IN PARTICULAR SPECIFICALLY DISCLAIM ANY AND ALL IMPLIED WARRANTIES OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY, AND FITNESS FOR A PARTICULAR PURPOSE.  FURTHERMORE, ORACLE AND ITS AFFILIATES DO NOT REPRESENT THAT ANY CUSTOMARY SECURITY REVIEW HAS BEEN PERFORMED WITH RESPECT TO ANY SOFTWARE, MATERIAL OR CONTENT CONTAINED OR PRODUCED WITHIN THIS REPOSITORY. IN ADDITION, AND WITHOUT LIMITING THE FOREGOING, THIRD PARTIES MAY HAVE POSTED SOFTWARE, MATERIAL OR CONTENT TO THIS REPOSITORY WITHOUT ANY REVIEW. USE AT YOUR OWN RISK. 
