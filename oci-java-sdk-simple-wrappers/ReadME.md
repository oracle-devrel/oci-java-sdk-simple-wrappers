# OCI Simple Wrappers

## What are they
These classes provide a simpler way to use the OCI Java SDK, in particular it handles things like retrieving data without the caller needing to know the details of the core OCI Java SDK but instead to focus more on the objects themselves. In some cases that may mean that additional API calls need to be made to the OCI server. Please be aware of the limitations below.

## Why were they created
The OCI Java SDK is very powerful, but it is very much structured around REST API concepts, for example retrieving a set of objects requires the users to construct requests using the request builders which don't use the returned objects (and in some cases there are multiple levels of builder / details / needed to create the request). Then after submitting a request to manage pagination (or at least to use a pagination class in the API) and so on. These classes make it a lot easier to interact with OCI, and to reduce the complexity of the code you'd need to write, though they do however reduce the flexibility. Fortunately however it's perfectly possible to mix these classes and the underlying OCI Java SDK for the situations where you do need the additional flexibility, and are willing to do the additional work to use the OCI Java SDK directly.

## Limitations (deliberate and otherwise)
This code does not even attempt to provide access to the full API, it was created to make my life easier when doing things that I needed. Usually this is retrieving information, and creating some resources. This however does not mean that all associated resources can be created, for example with the IoT service you are able to retrieve most information around IoT Domain Groups, IoT Domains etc, but you can't currently create them as that tends to be a one off exercise, usually done by terraform or similar. However for tasks that may well be done programmatically there is usually support for doing so (e.g. creating a IoT Digital Twin Instance)

## License
These are covered by the **The Universal Permissive License (UPL), Version 1.0** There is absolutely no support or responsibility taken for any of this code, you use totally at your own risk.

Copyright (c) 2026 Oracle and/or its affiliates.