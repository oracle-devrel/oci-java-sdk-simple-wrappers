/*Copyright (c) 2023 Oracle and/or its affiliates.

The Universal Permissive License (UPL), Version 1.0

Subject to the condition set forth below, permission is hereby granted to any
person obtaining a copy of this software, associated documentation and/or data
(collectively the "Software"), free of charge and under any and all copyright
rights in the Software, and any and all patent rights owned or freely
licensable by each licensor hereunder covering either (i) the unmodified
Software as contributed to or provided by such licensor, or (ii) the Larger
Works (as defined below), to deal in both

(a) the Software, and
(b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
one is included with the Software (each a "Larger Work" to which the Software
is contributed by such licensors),

without restriction, including without limitation the rights to copy, create
derivative works of, display, perform, and distribute the Software and make,
use, sell, offer for sale, import, export, have made, and have sold the
Software and the Larger Work(s), and to sublicense the foregoing rights on
either these or other terms.

This license is subject to the following condition:
The above copyright notice and either this complete permission notice or at
a minimum a reference to the UPL must be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package com.oracle.timg.oci.identity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.bmc.identity.Identity;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.model.AvailabilityDomain;
import com.oracle.bmc.identity.model.Compartment;
import com.oracle.bmc.identity.model.Compartment.LifecycleState;
import com.oracle.bmc.identity.model.CreateCompartmentDetails;
import com.oracle.bmc.identity.requests.CreateCompartmentRequest;
import com.oracle.bmc.identity.requests.DeleteCompartmentRequest;
import com.oracle.bmc.identity.requests.GetCompartmentRequest;
import com.oracle.bmc.identity.requests.ListAvailabilityDomainsRequest;
import com.oracle.bmc.identity.requests.ListCompartmentsRequest;
import com.oracle.bmc.identity.requests.ListRegionSubscriptionsRequest;
import com.oracle.bmc.identity.responses.CreateCompartmentResponse;
import com.oracle.bmc.identity.responses.GetCompartmentResponse;
import com.oracle.bmc.identity.responses.ListAvailabilityDomainsResponse;
import com.oracle.bmc.identity.responses.ListCompartmentsResponse;
import com.oracle.bmc.identity.responses.ListRegionSubscriptionsResponse;
import com.oracle.timg.oci.authentication.AuthenticationProcessor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * This class is a wrapper around the OCI Sdk for identity, it allows you to
 * perform common identity operations
 */
@Slf4j
public class IdentityProcessor {
	private AuthenticationProcessor authProcessor;

	private Identity identityClient;

	private String homeRegion;
	private String currentRegion;

	/**
	 * creates a processor using the region set in the auth processor
	 * 
	 * @param authProcessor - must not be null
	 * @throws IllegalArgumentException
	 * @throws IOException
	 */
	public IdentityProcessor(@NonNull AuthenticationProcessor authProcessor)
			throws IllegalArgumentException, IOException {
		this.authProcessor = authProcessor;
		identityClient = IdentityClient.builder().region(this.authProcessor.getRegionName())
				.build(this.authProcessor.getProvider());
	}

	/**
	 * allows you to switch this processor to work on a different region. Generally
	 * this is pointless for modification operations as they use the identity
	 * mechanisms in the home region only
	 * 
	 * @param regionName
	 */
	public void setRegion(@NonNull String regionName) {
		// there are some operations that must be done in the home region, syncing on
		// the identity client means we can prevent region changes
		// at the cost of blocking if we're already there, but how often are
		// compartments created anyway ?
		synchronized (identityClient) {
			identityClient.setRegion(regionName);
			this.currentRegion = regionName;
		}
	}

	/**
	 * given a path like /dev/project/user will scan the compartment tree starting
	 * at the root compartment to find it. If at any stage in the "path" the
	 * specified compartment cannot be located then returns null
	 * 
	 * @param path - must not be null
	 * @return
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	public Compartment locateCompartmentByPath(@NonNull String path) throws IllegalArgumentException, Exception {
		return locateCompartmentByPath(path, authProcessor.getTenancyOCID());
	}

	/**
	 * given a path like /dev/project/user will scan the compartment tree starting
	 * at the specified compartment to find it. If at any stage in the "path" the
	 * specified compartment cannot be located then returns null
	 * 
	 * @param path               - must not be null
	 * @param startCompartmentId - must not be null
	 * @return
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	public Compartment locateCompartmentByPath(@NonNull String path, @NonNull String startCompartmentId)
			throws IllegalArgumentException, Exception {
		// if the path starts with / then remove the / and search in the tenancy root,
		// ignoring the start comparment
		if (path.startsWith("/")) {
			return locateCompartmentByPath(path.substring(1));
		}
		// if the start compartment id is null then default to the tenancy root
		if (startCompartmentId == null) {
			return locateCompartmentByPath(path);
		}
		String paths[] = path.split("/");
		Compartment currentCompartment = null;
		for (String compartmentName : paths) {
			String compartmentOCID = currentCompartment == null ? startCompartmentId : currentCompartment.getId();
			log.debug("Looking for compartment " + compartmentName + " in parent " + compartmentOCID);
			currentCompartment = locateCompartment(compartmentName, compartmentOCID);
			if (currentCompartment == null) {
				return null;
			}
		}
		return currentCompartment;
	}

	/**
	 * Try to locate the compartment with a name of comaprtmentName name in the
	 * tenancy root, returns null if it can't be found
	 * 
	 * @param comaprtmentName - must not be null
	 * @return
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	public Compartment locateCompartment(@NonNull String comaprtmentName) throws IllegalArgumentException, Exception {
		return locateCompartment(comaprtmentName, authProcessor.getTenancyOCID());
	}

	/**
	 * 
	 * Try to locate the compartment with a name of comaprtmentName name in the
	 * specified parent compartment, returns null if it can't be found
	 * 
	 * @param comaprtmentName - must not be null
	 * @param parent          - must not be null
	 * @return
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	public Compartment locateCompartment(@NonNull String comaprtmentName, @NonNull Compartment parent)
			throws IllegalArgumentException, Exception {
		return locateCompartment(comaprtmentName, parent.getId());
	}

	/**
	 * 
	 * Try to locate the compartment with a name of comaprtmentName name in the
	 * parent compartment with the given ocid, returns null if it can't be found
	 * 
	 * @param comaprtmentName - must not be null
	 * @param parentOcid      - must not be null
	 * @return
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	public Compartment locateCompartment(@NonNull String compartmentName, @NonNull String parentOcid)
			throws IllegalArgumentException, Exception {
		// locate the first active compartment with the matching name
		log.debug("looking for " + compartmentName + " in " + parentOcid);
		Iterable<Compartment> compartmentsIterable = identityClient.getPaginators()
				.listCompartmentsRecordIterator(ListCompartmentsRequest.builder().compartmentId(parentOcid)
						.lifecycleState(LifecycleState.Active).name(compartmentName).build());
		if (compartmentsIterable.iterator().hasNext()) {
			return compartmentsIterable.iterator().next();
		}
		return null;
	}

	/**
	 * create a compartment with the specified name in the tenancy root
	 * 
	 * @param name        - must not be null
	 * @param description - if null will be set to "Not provided"
	 * @return
	 * @throws Exception
	 */
	public Compartment createCompartment(@NonNull String name, String description) throws Exception {
		return createCompartment(name, description, authProcessor.getTenancyOCID());
	}

	/**
	 * 
	 * create a compartment with the specified name in the specified parent
	 * compartment
	 * 
	 * @param name        - must not be null
	 * @param description - if null will be set to "Not provided"
	 * @param parentOCID
	 * @return
	 * @throws Exception
	 */
	public Compartment createCompartment(@NonNull String name, String description, @NonNull String parentOCID)
			throws Exception {
		String ourDescription = description == null ? "Not provided" : description;
		// compartments can only be created, deleted or updated in the home reqion, so
		// if needed
		// do a temp switch to there, synchronise to ensure that someone else doesn't
		// change the region while
		// we're in there
		synchronized (identityClient) {
			boolean switchToHomeRegion = !this.getHomeRegion().equals(currentRegion);
			String savedRegion = currentRegion;
			if (switchToHomeRegion) {
				this.setRegion(homeRegion);
			}
			CreateCompartmentResponse createCompartmentResponse = identityClient.createCompartment(
					CreateCompartmentRequest.builder().createCompartmentDetails(CreateCompartmentDetails.builder()
							.name(name).description(ourDescription).compartmentId(parentOCID).build()).build());
			// wait for it to become available
			GetCompartmentResponse requestStatus = identityClient.getWaiters()
					.forCompartment(
							GetCompartmentRequest.builder()
									.compartmentId(createCompartmentResponse.getCompartment().getId()).build(),
							LifecycleState.Active)
					.execute();
			log.debug("Created compartment name in parent compartment ocid " + parentOCID);
			// if we has switched regions revert

			if (switchToHomeRegion) {
				this.setRegion(savedRegion);
			}
			return requestStatus.getCompartment();
		}
	}

	/**
	 * list compartments in the tenancy root
	 * 
	 * @return - list of zero or more compartments
	 */
	public List<Compartment> listChildCompartments() {
		return listChildCompartment(authProcessor.getTenancyOCID());
	}

	/**
	 * list compartments in the specified compartment
	 * 
	 * @param - parentCompartment - must not be null
	 * @return - list of zero or more compartments
	 */
	public List<Compartment> listChildCompartments(@NonNull Compartment parentCompartment) {
		return listChildCompartment(parentCompartment.getId());
	}

	/**
	 * list compartments in the compartment with the specified ocid
	 * 
	 * @param - parentCompartmentOCID - must not be null
	 * @return - list of zero or more compartments
	 */
	public List<Compartment> listChildCompartment(@NonNull String parentCompartmentOCID) {
		// locate the active compartments in the provided parent
		ListCompartmentsResponse response = identityClient
				.listCompartments(ListCompartmentsRequest.builder().sortBy(ListCompartmentsRequest.SortBy.Name)
						.compartmentId(parentCompartmentOCID).lifecycleState(LifecycleState.Active).build());
		return response.getItems();
	}

	/**
	 * delete the specified compartment, this is an async process and can take a
	 * while, comparements which have any contents in any region (not just the home
	 * region) cannot be deleted
	 * 
	 * @param compartment - must not be null
	 * @throws Exception
	 */
	public void deleteCompartment(@NonNull Compartment compartment) throws Exception {
		deleteCompartment(compartment.getId());
	}

	/**
	 * delete the compartment with the specified ocid, this is an async process and
	 * can take a while, comparements which have any contents in any region (not
	 * just the home region) cannot be deleted
	 * 
	 * @param compartmentOcid - must not be null
	 * @throws Exception
	 */
	public void deleteCompartment(@NonNull String compartmentOcid) throws Exception {
		// compartments can only be created, deleted or updated in the home reqion, so
		// if needed
		// do a temp switch to there, synchronise to ensure that someone else doesn't
		// change the region while
		// we're in there
		synchronized (identityClient) {
			boolean switchToHomeRegion = !this.getHomeRegion().equals(currentRegion);
			String savedRegion = currentRegion;
			if (switchToHomeRegion) {
				this.setRegion(homeRegion);
			}

			identityClient.deleteCompartment(DeleteCompartmentRequest.builder().compartmentId(compartmentOcid).build());
			identityClient.getWaiters()
					.forCompartment(GetCompartmentRequest.builder().compartmentId(compartmentOcid).build(),
							LifecycleState.Deleted)
					.execute();
			this.setRegion(savedRegion);
		}
	}

	/**
	 * get the availability domains in the current region for the specified
	 * compartment
	 * 
	 * @param compartment - must not be null
	 * @return - a list of zero or more availability domains
	 */
	public List<AvailabilityDomain> getAvailabilityDomains(@NonNull Compartment compartment) {
		return getAvailabilityDomains(compartment.getId());
	}

	/**
	 * get the availability domains in the current region for the compartment with
	 * the specified ocid
	 * 
	 * @param compartmentOcid - must not be null
	 * @return - a list of zero or more availability domains
	 */
	public List<AvailabilityDomain> getAvailabilityDomains(@NonNull String compartmentOcid) {
		ListAvailabilityDomainsResponse listAvailabilityDomainsResponse = identityClient.listAvailabilityDomains(
				ListAvailabilityDomainsRequest.builder().compartmentId(compartmentOcid).build());
		return listAvailabilityDomainsResponse.getItems();
	}

	/**
	 * Locates the home region for this tenancy
	 * 
	 * @return
	 */
	public String getHomeRegion() {
		if (homeRegion == null) {
			ListRegionSubscriptionsResponse response = identityClient.listRegionSubscriptions(
					ListRegionSubscriptionsRequest.builder().tenancyId(authProcessor.getTenancyOCID()).build());
			// locate the first region that is the home region (there can be only one !)
			homeRegion = response.getItems().stream().filter(region -> region.getIsHomeRegion())
					.map(region -> region.getRegionName()).findFirst().orElse(null);
		}
		return homeRegion;
	}

	/**
	 * lists regions available to this tenancy
	 * 
	 * @return list of zero or more regions
	 */
	public List<String> listRegions() {
		return listRegions(false, (String) null);
	}

	/**
	 * lists regions available to this tenancy
	 * 
	 * @param excludeHomeRegion - if true the returned list will not include the
	 *                          home region (i.e. it will include regions other than
	 *                          the home region
	 * @param excludeRegionName - if non null is a region to be excluded from the
	 *                          returned results
	 * @return list of zero or more regions
	 */
	public List<String> listRegions(boolean excludeHomeRegion, String excludeRegionName) {
		return listRegions(excludeHomeRegion, excludeRegionName == null ? null : Arrays.asList(excludeRegionName));
	}

	/**
	 * lists regions available to this tenancy optionally excluding the home region
	 * 
	 * @param excludeHomeRegion - if true the returned list will not include the
	 *                          home region (i.e. it will only include regions other
	 *                          than the home region
	 * @return list of zero or more regions
	 */
	public List<String> listRegions(boolean excludeHomeRegion) {
		return listRegions(excludeHomeRegion, Arrays.asList());
	}

	/**
	 * lists regions available to this tenancy including the home region, but
	 * excliding the specified region
	 * 
	 * @param excludeRegionName - if non null is a region to be excluded from the
	 *                          returned results
	 * @return list of zero or more regions
	 */
	public List<String> listRegions(String excludeRegionName) {
		return listRegions(false, excludeRegionName == null ? null : Arrays.asList(excludeRegionName));
	}

	/**
	 * lists regions available to this tenancy including the home region, but
	 * excluding any region names in the excludeRegionNames list. If it's null all
	 * regions will be returned.
	 * 
	 * @param excludeRegionNames - if non null a list of region names to exclude
	 * @return list of zero or more regions
	 */
	public List<String> listRegions(@NonNull List<String> excludeRegionNames) {
		return listRegions(false, excludeRegionNames);
	}

	/**
	 * 
	 * lists regions available to this tenancy optionally excluding the home region,
	 * and regions in the excluderegion names list
	 * 
	 * @param excludeHomeRegion  - if true the returned list will not include the
	 *                           home region (i.e. it will only include regions
	 *                           other than the home region
	 * @param excludeRegionNames - if non null a list of region names to exclude
	 * @return list of zero or more regions
	 */

	public List<String> listRegions(boolean excludeHomeRegion, List<String> excludeRegionNames) {
		final List<String> exclude = excludeRegionNames == null ? new ArrayList<>() : excludeRegionNames;
		ListRegionSubscriptionsResponse response = identityClient.listRegionSubscriptions(
				ListRegionSubscriptionsRequest.builder().tenancyId(authProcessor.getTenancyOCID()).build());
		// if excludeHomeRegion then include remove the home region in the filter,
		// otherwise include everything
		return response.getItems().stream().filter(region -> !exclude.contains(region.getRegionName()))
				.filter(region -> excludeHomeRegion ? !region.getIsHomeRegion() : true)
				.map(region -> region.getRegionName()).sorted().toList();
	}
}
