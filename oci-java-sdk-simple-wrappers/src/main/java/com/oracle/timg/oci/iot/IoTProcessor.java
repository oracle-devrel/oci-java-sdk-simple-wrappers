/*Copyright (c) 2025 Oracle and/or its affiliates.

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
package com.oracle.timg.oci.iot;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.apache.http.HttpStatus;

import com.oracle.bmc.identity.model.Compartment;
import com.oracle.bmc.iot.IotClient;
import com.oracle.bmc.iot.model.CreateDigitalTwinInstanceDetails;
import com.oracle.bmc.iot.model.DigitalTwinAdapter;
import com.oracle.bmc.iot.model.DigitalTwinAdapterSummary;
import com.oracle.bmc.iot.model.DigitalTwinInstance;
import com.oracle.bmc.iot.model.DigitalTwinInstanceSummary;
import com.oracle.bmc.iot.model.DigitalTwinModel;
import com.oracle.bmc.iot.model.DigitalTwinModelSummary;
import com.oracle.bmc.iot.model.IotDomain;
import com.oracle.bmc.iot.model.IotDomainGroup;
import com.oracle.bmc.iot.model.IotDomainGroupSummary;
import com.oracle.bmc.iot.model.IotDomainSummary;
import com.oracle.bmc.iot.requests.CreateDigitalTwinInstanceRequest;
import com.oracle.bmc.iot.requests.DeleteDigitalTwinInstanceRequest;
import com.oracle.bmc.iot.requests.GetDigitalTwinAdapterRequest;
import com.oracle.bmc.iot.requests.GetDigitalTwinInstanceContentRequest;
import com.oracle.bmc.iot.requests.GetDigitalTwinInstanceRequest;
import com.oracle.bmc.iot.requests.GetDigitalTwinModelRequest;
import com.oracle.bmc.iot.requests.GetIotDomainGroupRequest;
import com.oracle.bmc.iot.requests.GetIotDomainRequest;
import com.oracle.bmc.iot.requests.ListDigitalTwinAdaptersRequest;
import com.oracle.bmc.iot.requests.ListDigitalTwinInstancesRequest;
import com.oracle.bmc.iot.requests.ListDigitalTwinModelsRequest;
import com.oracle.bmc.iot.requests.ListIotDomainGroupsRequest;
import com.oracle.bmc.iot.requests.ListIotDomainsRequest;
import com.oracle.bmc.iot.responses.CreateDigitalTwinInstanceResponse;
import com.oracle.bmc.iot.responses.DeleteDigitalTwinInstanceResponse;
import com.oracle.bmc.iot.responses.GetDigitalTwinAdapterResponse;
import com.oracle.bmc.iot.responses.GetDigitalTwinInstanceContentResponse;
import com.oracle.bmc.iot.responses.GetDigitalTwinInstanceResponse;
import com.oracle.bmc.iot.responses.GetDigitalTwinModelResponse;
import com.oracle.bmc.iot.responses.GetIotDomainGroupResponse;
import com.oracle.bmc.iot.responses.GetIotDomainResponse;
import com.oracle.bmc.vault.model.Secret;
import com.oracle.timg.oci.authentication.AuthenticationProcessor;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * this class provides a wrapper around the oci sdk for iot and makes things
 * easier
 */
@Slf4j
public class IoTProcessor {
	private final AuthenticationProcessor authProcessor;
	@Getter
	private final IotClient iotClient;

	/**
	 * creates an instance which will use the provided AuthenticationProcessor for
	 * authentication
	 * 
	 * @param authProcessor
	 */
	public IoTProcessor(AuthenticationProcessor authProcessor) {
		this.authProcessor = authProcessor;
		iotClient = IotClient.builder().region(authProcessor.getRegionName()).build(authProcessor.getProvider());
	}

	/**
	 * allows you to change the region form the default provided by the
	 * AuthenticationProcessor
	 * 
	 * @param regionName
	 */
	public void setRegion(@NonNull String regionName) {
		iotClient.setRegion(regionName);
	}

	/**
	 * gets a list of summaries of all IotDomainGroups in the tenancy root
	 * compartment
	 * 
	 * @return
	 */
	public List<IotDomainGroupSummary> listIoTDomainGroupSummariesInTenancy() {
		return listIoTDomainGroupSummariesInCompartment(authProcessor.getTenancyOCID());
	}

	/**
	 * gets a list of summaries of all IotDomainGroups in the specified parent
	 * compartment
	 * 
	 * @param parentCompartment
	 * @return
	 */
	public List<IotDomainGroupSummary> listIoTDomainGroupSummariesInCompartment(
			@NonNull Compartment parentCompartment) {
		return listIoTDomainGroupSummariesInCompartment(authProcessor.getTenancyOCID());
	}

	/**
	 * gets a list of summaries of all IotDomainGroups in the compartment with the
	 * specified parent compartment ocid
	 * 
	 * @param parentCompartmentOcid
	 * @return
	 */
	public List<IotDomainGroupSummary> listIoTDomainGroupSummariesInCompartment(@NonNull String parentCompartmentOcid) {
		return listIoTDomainGroupSummariesInCompartment(parentCompartmentOcid, null);
	}

	/**
	 * generate a list of all domain group summaries in a compartment, if
	 * displayName is not null limits it to only those matching the display name
	 * 
	 * @param parentCompartmentOcid
	 * @param displayName
	 * 
	 * @return
	 */
	public List<IotDomainGroupSummary> listIoTDomainGroupSummariesInCompartment(@NonNull Compartment parentCompartment,
			String displayName) {
		return listIoTDomainGroupSummariesInCompartment(parentCompartment.getId(), displayName);
	}

	/**
	 * generate a list of all domain group summaries in a compartment, if
	 * displayName is not null limits it to only those matching the display name
	 * 
	 * @param parentCompartmentOcid
	 * @param displayName
	 * 
	 * @return
	 */
	public List<IotDomainGroupSummary> listIoTDomainGroupSummariesInCompartment(@NonNull String parentCompartmentOcid,
			String displayName) {
		ListIotDomainGroupsRequest.Builder requestBuilder = ListIotDomainGroupsRequest.builder()
				.compartmentId(parentCompartmentOcid);
		if (displayName != null) {
			requestBuilder.displayName(displayName);
		}
		Iterable<IotDomainGroupSummary> domainGroupsSummaries = iotClient.getPaginators()
				.listIotDomainGroupsRecordIterator(requestBuilder.build());
		return StreamSupport.stream(domainGroupsSummaries.spliterator(), false).toList();
	}

	/**
	 * gets a list of all IotDomainGroups in the tenancy root compartment
	 * 
	 * @return
	 */
	public List<IotDomainGroup> listIoTDomainGroupsInTenancy() {
		return listIoTDomainGroupsInCompartment(authProcessor.getTenancyOCID());
	}

	/**
	 * returns a list of all IotDomainGroups in the compartment with the specified
	 * parent compartment ocid
	 * 
	 * @param parentCompartmentOcid
	 * @return
	 */
	public List<IotDomainGroup> listIoTDomainGroupsInCompartment(@NonNull String parentCompartmentOcid) {
		return listIoTDomainGroupsInCompartment(parentCompartmentOcid, null);
	}

	/**
	 * returns a list of all IotDomainGroups in the specified parent compartment
	 * 
	 * @param parentCompartmentOcid
	 * @return
	 */
	public List<IotDomainGroup> listIoTDomainGroupsInCompartment(@NonNull Compartment parentCompartment) {
		return listIoTDomainGroupsInCompartment(parentCompartment.getId(), null);
	}

	/**
	 * * returns a list of all IotDomainGroups with a matching displayName in the
	 * compartment with the specified parent compartment ocid. If displayName is
	 * null returns all domain groups
	 * 
	 * @param parentCompartmentOcid
	 * @param displayName
	 * 
	 * @return
	 */
	public List<IotDomainGroup> listIoTDomainGroupsInCompartment(@NonNull String parentCompartmentOcid,
			String displayName) {
		List<IotDomainGroupSummary> domainGroupSummaries = listIoTDomainGroupSummariesInCompartment(
				parentCompartmentOcid, displayName);
		return domainGroupSummaries.stream().map(dsg -> getIotDomainGroup(dsg)).toList();
	}

	/**
	 * gets the **FIRST** IotDomainGroupSummary with a matching display name in the
	 * compartment with the specified OCID, or null if there are no matches
	 * 
	 * @param parentCompartmentOcid
	 * @param displayName
	 * 
	 * @return
	 */
	public IotDomainGroupSummary getIotDomainGroupSummary(@NonNull String parentCompartmentOcid,
			@NonNull String displayName) {
		List<IotDomainGroupSummary> iotDomainGroupSummaries = listIoTDomainGroupSummariesInCompartment(
				parentCompartmentOcid, displayName);
		if (iotDomainGroupSummaries.isEmpty()) {
			return null;
		}
		return iotDomainGroupSummaries.getFirst();
	}

	/**
	 * gets the **FIRST** IotDomainGroup with matching displayName in the
	 * compartment with the specified parent compartment ocid, or null if there are
	 * no matches
	 * 
	 * @param parentCompartmentOcid
	 * @param displayName
	 * 
	 * @return
	 */
	public IotDomainGroup getIotDomainGroup(@NonNull String parentCompartmentOcid, @NonNull String displayName) {
		List<IotDomainGroupSummary> iotDomainGroupSummaries = listIoTDomainGroupSummariesInCompartment(
				parentCompartmentOcid, displayName);
		if (iotDomainGroupSummaries.isEmpty()) {
			return null;
		}
		return getIotDomainGroup(iotDomainGroupSummaries.getFirst());
	}

	/**
	 * gets the IotDomainGroup details from the provided summary
	 * 
	 * @param iotDomainGroupSummary
	 * @return
	 */
	public IotDomainGroup getIotDomainGroup(@NonNull IotDomainGroupSummary iotDomainGroupSummary) {
		GetIotDomainGroupResponse resp = iotClient.getIotDomainGroup(
				GetIotDomainGroupRequest.builder().iotDomainGroupId(iotDomainGroupSummary.getId()).build());
		return resp.getIotDomainGroup();
	}

	/**
	 * get a summary of all IotDomains in the specified IoTDomainGroup
	 * 
	 * @param iotDomainGroupSummary
	 * @return
	 */
	public List<IotDomainSummary> listIotDomainSummariesInIotDomainGroup(
			@NonNull IotDomainGroupSummary iotDomainGroupSummary) {
		return listIotDomainSummariesInIotDomainGroup(iotDomainGroupSummary.getId());
	}

	/**
	 * get a summary of all IotDomains in the specified IoTDomainGroup
	 * 
	 * @param iotDomainGroup
	 * @return
	 */

	public List<IotDomainSummary> listIotDomainSummariesInIotDomainGroup(@NonNull IotDomainGroup iotDomainGroup) {
		return listIotDomainSummariesInIotDomainGroup(iotDomainGroup.getId());

	}

	/**
	 * get a summary of all IotDomains in the specified IoTDomainGroup
	 * 
	 * @param iotDomainGroupOcid
	 * @return
	 */
	public List<IotDomainSummary> listIotDomainSummariesInIotDomainGroup(@NonNull String iotDomainGroupOcid) {
		return listIotDomainSummariesInIotDomainGroup(iotDomainGroupOcid, null);
	}

	/**
	 * get a summary of all IotDomains in the specified IoTDomainGroup, if
	 * displayName is not null limits on only domains with a matching name
	 * 
	 * @param iotDomainGroupSummary
	 * @Param displayName
	 * @return
	 */
	public List<IotDomainSummary> listIotDomainSummariesInIotDomainGroup(
			@NonNull IotDomainGroupSummary iotDomainGroupSummary, String displayName) {
		return listIotDomainSummariesInIotDomainGroup(iotDomainGroupSummary.getId(), displayName);

	}

	/**
	 * get a summary of all IotDomains in the specified IoTDomainGroup, if
	 * displayName is not null limits on only domains with a matching name
	 * 
	 * @param iotDomainGroup
	 * @Param displayName
	 * @return
	 */
	public List<IotDomainSummary> listIotDomainSummariesInIotDomainGroup(@NonNull IotDomainGroup iotDomainGroup,
			String displayName) {
		return listIotDomainSummariesInIotDomainGroup(iotDomainGroup.getId(), displayName);
	}

	/**
	 * get a summary of all IotDomains in the specified IoTDomainGroup, if
	 * displayName is not null limits on only domains with a matching name
	 * 
	 * @param iotDomainGroupSummaryOcid
	 * @Param displayName
	 * @return
	 */
	public List<IotDomainSummary> listIotDomainSummariesInIotDomainGroup(@NonNull String iotDomainGroupOcid,
			String displayName) {
		ListIotDomainsRequest.Builder requestBuilder = ListIotDomainsRequest.builder()
				.iotDomainGroupId(iotDomainGroupOcid);
		if (displayName != null) {
			requestBuilder.displayName(displayName);
		}
		Iterable<IotDomainSummary> domainGroupSummaries = iotClient.getPaginators()
				.listIotDomainsRecordIterator(requestBuilder.build());
		return StreamSupport.stream(domainGroupSummaries.spliterator(), false).toList();
	}

	/**
	 * get a list of all IotDomains in the specified IoTDomainGroupSummary
	 * 
	 * @param iotDomainGroupSummary
	 * @return
	 */
	public List<IotDomain> listIotDomainsInIotDomainGroup(@NonNull IotDomainGroupSummary iotDomainGroupSummary) {
		return listIotDomainsInIotDomainGroup(iotDomainGroupSummary.getId());
	}

	/**
	 * get a list of all IotDomains in the specified IoTDomainGroup
	 * 
	 * @param iotDomainGroup
	 * @return
	 */
	public List<IotDomain> listIotDomainsInIotDomainGroup(@NonNull IotDomainGroup iotDomainGroup) {
		return listIotDomainsInIotDomainGroup(iotDomainGroup.getId());

	}

	/**
	 * get a list of all IotDomains in the specified IoTDomainGroupSummary
	 * 
	 * @param iotDomainGroupOcid
	 * @return
	 */
	public List<IotDomain> listIotDomainsInIotDomainGroup(@NonNull String iotDomainGroupOcid) {
		return listIotDomainsInIotDomainGroup(iotDomainGroupOcid, null);
	}

	/**
	 * get a list of all IotDomains in the specified IoTDomainGroupSummary, if
	 * displayName is not null limits on only domains with a matching name
	 * 
	 * @param iotDomainGroupSummary
	 * @Param displayName
	 * @return
	 */
	public List<IotDomain> listIotDomainsInIotDomainGroup(@NonNull IotDomainGroupSummary iotDomainGroupSummary,
			String displayName) {
		return listIotDomainsInIotDomainGroup(iotDomainGroupSummary.getId(), displayName);

	}

	/**
	 * get a list of all IotDomains in the specified IoTDomainGroup, if displayName
	 * is not null limits on only domains with a matching name
	 * 
	 * @param iotDomainGroup
	 * @Param displayName
	 * @return
	 */
	public List<IotDomain> listIotDomainsInIotDomainGroup(@NonNull IotDomainGroup iotDomainGroup, String displayName) {
		return listIotDomainsInIotDomainGroup(iotDomainGroup.getId(), displayName);
	}

	/**
	 * get a list of all IotDomains in the specified IoTDomainGroup, if displayName
	 * is not null limits on only domains with a matching name
	 * 
	 * @param iotDomainGroupOcid
	 * @Param displayName
	 * @return
	 */
	public List<IotDomain> listIotDomainsInIotDomainGroup(@NonNull String iotDomainGroupOcid, String displayName) {
		List<IotDomainSummary> domainSummaries = listIotDomainSummariesInIotDomainGroup(iotDomainGroupOcid,
				displayName);
		return domainSummaries.stream().map(ds -> getIotDomain(ds)).toList();
	}

	/**
	 * get the iotdomain (i.e. with full details) from the specified summary
	 * 
	 * @param iotDomainSummary
	 * @return
	 */
	public IotDomain getIotDomain(@NonNull IotDomainSummary iotDomainSummary) {
		GetIotDomainResponse resp = iotClient
				.getIotDomain(GetIotDomainRequest.builder().iotDomainId(iotDomainSummary.getId()).build());
		return resp.getIotDomain();
	}

	/**
	 * get a list of all IotDomains in the specified IoTDomainGroup, if displayName
	 * is not null limits on only domains with a matching name
	 * 
	 * @param iotDomainGroup
	 * @Param displayName
	 * @return
	 */
	public IotDomain getIotDomain(@NonNull IotDomainGroup iotDomainGroup, @NonNull String displayName) {
		return getIotDomain(iotDomainGroup.getId(), displayName);
	}

	/**
	 * get a list of all IotDomains in the specified IoTDomainGroup, if displayName
	 * is not null limits on only domains with a matching name
	 * 
	 * @param iotDomainGroup
	 * @Param displayName
	 * @return
	 */
	public IotDomain getIotDomain(@NonNull IotDomainGroupSummary iotDomainGroupSummary, @NonNull String displayName) {
		return getIotDomain(iotDomainGroupSummary.getId(), displayName);
	}

	/**
	 * get a list of all IotDomains in the specified IoTDomainGroup, if displayName
	 * is not null limits on only domains with a matching name
	 * 
	 * @param iotDomainGroupOcid
	 * @Param displayName
	 * @return
	 */
	public IotDomain getIotDomain(@NonNull String iotDomainGroupOcid, @NonNull String displayName) {
		List<IotDomainSummary> iotDomainSummaries = listIotDomainSummariesInIotDomainGroup(iotDomainGroupOcid,
				displayName);
		if (iotDomainSummaries.isEmpty()) {
			return null;
		}
		return getIotDomain(iotDomainSummaries.getFirst());
	}

	/**
	 * get a list of all DigitalTwinModelSummary in the specified IoTDomainSummary
	 * 
	 * @param iotDomainSummary
	 * @return
	 */
	public List<DigitalTwinModelSummary> listDigitalTwinModelSummaries(@NonNull IotDomainSummary iotDomainSummary) {
		return listDigitalTwinModelSummaries(iotDomainSummary.getId());
	}

	/**
	 * get a list of all DigitalTwinModelSummary in the specified IoTDomain
	 * 
	 * @param iotDomain
	 * @return
	 */
	public List<DigitalTwinModelSummary> listDigitalTwinModelSummaries(@NonNull IotDomain iotDomain) {
		return listDigitalTwinModelSummaries(iotDomain.getId());

	}

	/**
	 * get a list of all DigitalTwinModelSummary in the specified IoTDomain
	 * 
	 * @param iotDomainOcid
	 * @return
	 */
	public List<DigitalTwinModelSummary> listDigitalTwinModelSummaries(@NonNull String iotDomainOcid) {
		return listDigitalTwinModelSummaries(iotDomainOcid, null);

	}

	/**
	 * get a list of all DigitalTwinModelSummary in the specified IoTDomainSummary,
	 * if displayName is not null limits on only domains with a matching name
	 * 
	 * @param iotDomainSummary
	 * @Param displayName
	 * @return
	 */
	public List<DigitalTwinModelSummary> listDigitalTwinModelSummaries(@NonNull IotDomainSummary iotDomainSummary,
			String displayName) {
		return listDigitalTwinModelSummaries(iotDomainSummary.getId(), displayName);
	}

	/**
	 * get a list of all DigitalTwinModelSummary in the specified IoTDomain, if
	 * displayName is not null limits on only domains with a matching name
	 * 
	 * @param iotDomain
	 * @Param displayName
	 * @return
	 */
	public List<DigitalTwinModelSummary> listDigitalTwinModelSummaries(@NonNull IotDomain iotDomain,
			String displayName) {
		return listDigitalTwinModelSummaries(iotDomain.getId(), displayName);

	}

	/**
	 * get a list of all DigitalTwinModelSummary in the specified IoTDomain, if
	 * displayName is not null limits on only domains with a matching name
	 * 
	 * @param iotDomainOcid
	 * @Param displayName
	 * @return
	 */
	public List<DigitalTwinModelSummary> listDigitalTwinModelSummaries(@NonNull String iotDomainOcid,
			String displayName) {
		ListDigitalTwinModelsRequest.Builder requestBuilder = ListDigitalTwinModelsRequest.builder()
				.iotDomainId(iotDomainOcid);
		if (displayName != null) {
			requestBuilder.displayName(displayName);
		}
		Iterable<DigitalTwinModelSummary> digitalTwinModelSummaries = iotClient.getPaginators()
				.listDigitalTwinModelsRecordIterator(requestBuilder.build());
		return StreamSupport.stream(digitalTwinModelSummaries.spliterator(), false).toList();
	}

	/**
	 * get a list of all DigitalTwinModel in the specified IoTDomainSummary
	 * 
	 * @param iotDomainSummary
	 * @return
	 */
	public List<DigitalTwinModel> listDigitalTwinModels(@NonNull IotDomainSummary iotDomainSummary) {
		return listDigitalTwinModels(iotDomainSummary.getId());
	}

	/**
	 * get a list of all DigitalTwinModel in the specified IoTDomain
	 * 
	 * @param iotDomain
	 * @return
	 */
	public List<DigitalTwinModel> listDigitalTwinModels(@NonNull IotDomain iotDomain) {
		return listDigitalTwinModels(iotDomain.getId());

	}

	/**
	 * get a list of all DigitalTwinModel in the specified IoTDomain
	 * 
	 * @param iotDomainOcid
	 * @return
	 */
	public List<DigitalTwinModel> listDigitalTwinModels(@NonNull String iotDomainOcid) {
		return listDigitalTwinModels(iotDomainOcid, null);

	}

	/**
	 * get a list of all DigitalTwinModel in the specified IoTDomainSummary, if
	 * displayName is not null it's used to limit the results
	 * 
	 * @param iotDomainSummary
	 * @param displayName
	 * @return
	 */
	public List<DigitalTwinModel> listDigitalTwinModels(@NonNull IotDomainSummary iotDomainSummary,
			String displayName) {
		return listDigitalTwinModels(iotDomainSummary.getId(), displayName);
	}

	/**
	 * get a list of all DigitalTwinModel in the specified IoTDomain, if displayName
	 * is not null it's used to limit the results
	 * 
	 * @param iotDomain
	 * @param displayName
	 * @return
	 */
	public List<DigitalTwinModel> listDigitalTwinModels(@NonNull IotDomain iotDomain, String displayName) {
		return listDigitalTwinModels(iotDomain.getId(), displayName);

	}

	/**
	 * get a list of all DigitalTwinModel in the specified IoTDomain, if displayName
	 * is not null it's used to limit the results
	 * 
	 * @param iotDomainOcid
	 * @param displayName
	 * @return
	 */
	public List<DigitalTwinModel> listDigitalTwinModels(@NonNull String iotDomainOcid, String displayName) {
		List<DigitalTwinModelSummary> modelSummaries = listDigitalTwinModelSummaries(iotDomainOcid, displayName);
		return modelSummaries.stream().map(ms -> getDigitalTwinModel(ms)).toList();
	}

	/**
	 * get gets DigitalTwinModel in the specified IoTDomainSummary, with the
	 * specified displayName, if there are no matches returns null
	 * 
	 * @param iotDomainSummary
	 * @param displayName
	 * @return
	 */
	public DigitalTwinModel getDigitalTwinModel(@NonNull IotDomainSummary iotDomainSummary,
			@NonNull String displayName) {
		return getDigitalTwinModel(iotDomainSummary.getId(), displayName);
	}

	/**
	 * get gets DigitalTwinModel in the specified IoTDomain, with the specified
	 * displayName, if there are no matches returns null
	 * 
	 * @param iotDomain
	 * @param displayName
	 * @return
	 */
	public DigitalTwinModel getDigitalTwinModel(@NonNull IotDomain iotDomain, @NonNull String displayName) {
		return getDigitalTwinModel(iotDomain.getId(), displayName);

	}

	/**
	 * get gets DigitalTwinModel in the specified IoTDomain, with the specified
	 * displayName, if there are no matches returns null
	 * 
	 * @param iotDomainOcid
	 * @param displayName
	 * @return
	 */
	public DigitalTwinModel getDigitalTwinModel(@NonNull String iotDomainOcid, @NonNull String displayName) {
		List<DigitalTwinModelSummary> digitalTwinModelSummaries = listDigitalTwinModelSummaries(iotDomainOcid,
				displayName);
		if (digitalTwinModelSummaries.isEmpty()) {
			return null;
		}
		return getDigitalTwinModel(digitalTwinModelSummaries.getFirst());
	}

	/**
	 * get gets DigitalTwinModel from the specified DigitalTwinModelSummary
	 * 
	 * @param digitalTwinModelSummary
	 * @return
	 */
	public DigitalTwinModel getDigitalTwinModel(@NonNull DigitalTwinModelSummary digitalTwinModelSummary) {
		GetDigitalTwinModelResponse resp = iotClient.getDigitalTwinModel(
				GetDigitalTwinModelRequest.builder().digitalTwinModelId(digitalTwinModelSummary.getId()).build());
		return resp.getDigitalTwinModel();
	}

	/**
	 * get all DigitalTwinAdapterSummary in the specified IotDomain
	 * 
	 * @param iotDomainSummary
	 * @return
	 */
	public List<DigitalTwinAdapterSummary> listDigitalTwinAdapterSummaries(@NonNull IotDomainSummary iotDomainSummary) {
		return listDigitalTwinAdapterSummaries(iotDomainSummary.getId());
	}

	/**
	 * get all DigitalTwinAdapterSummary in the specified IotDomain
	 * 
	 * @param iotDomain
	 * @return
	 */
	public List<DigitalTwinAdapterSummary> listDigitalTwinAdapterSummaries(@NonNull IotDomain iotDomain) {
		return listDigitalTwinAdapterSummaries(iotDomain.getId());

	}

	/**
	 * get all DigitalTwinAdapterSummary in the specified IotDomain
	 * 
	 * @param iotDomainOcid
	 * @return
	 */
	public List<DigitalTwinAdapterSummary> listDigitalTwinAdapterSummaries(@NonNull String iotDomainOcid) {
		return listDigitalTwinAdapterSummaries(iotDomainOcid, null);

	}

	/**
	 * get all DigitalTwinAdapterSummary in the specified IotDomain, if displayName
	 * is non null limits to only results with that displayName
	 * 
	 * @param iotDomainSummary
	 * @return
	 */
	public List<DigitalTwinAdapterSummary> listDigitalTwinAdapterSummaries(@NonNull IotDomainSummary iotDomainSummary,
			String displayName) {
		return listDigitalTwinAdapterSummaries(iotDomainSummary.getId(), displayName);
	}

	/**
	 * get all DigitalTwinAdapterSummary in the specified IotDomain, if displayName
	 * is non null limits to only results with that displayName
	 * 
	 * @param iotDomain
	 * @return
	 */
	public List<DigitalTwinAdapterSummary> listDigitalTwinAdapterSummaries(@NonNull IotDomain iotDomain,
			String displayName) {
		return listDigitalTwinAdapterSummaries(iotDomain.getId(), displayName);

	}

	/**
	 * get all DigitalTwinAdapterSummary in the specified IotDomain, if displayName
	 * is non null limits to only results with that displayName
	 * 
	 * @param iotDomainOcid
	 * @return
	 */
	public List<DigitalTwinAdapterSummary> listDigitalTwinAdapterSummaries(@NonNull String iotDomainOcid,
			String displayName) {
		ListDigitalTwinAdaptersRequest.Builder requestBuilder = ListDigitalTwinAdaptersRequest.builder()
				.iotDomainId(iotDomainOcid);
		if (displayName != null) {
			requestBuilder.displayName(displayName);
		}
		Iterable<DigitalTwinAdapterSummary> digitalTwinAdapterSummaries = iotClient.getPaginators()
				.listDigitalTwinAdaptersRecordIterator(requestBuilder.build());
		return StreamSupport.stream(digitalTwinAdapterSummaries.spliterator(), false).toList();
	}

	/**
	 * get a list of all DigitalTwinAdapter in the specified IoTDomainSummary
	 * 
	 * @param iotDomainSummary
	 * @return
	 */
	public List<DigitalTwinAdapter> listDigitalTwinAdapters(@NonNull IotDomainSummary iotDomainSummary) {
		return listDigitalTwinAdapters(iotDomainSummary.getId());
	}

	/**
	 * get a list of all DigitalTwinAdapter in the specified IoTDomain
	 * 
	 * @param iotDomain
	 * @return
	 */
	public List<DigitalTwinAdapter> listDigitalTwinAdapters(@NonNull IotDomain iotDomain) {
		return listDigitalTwinAdapters(iotDomain.getId());

	}

	/**
	 * get a list of all DigitalTwinAdapter in the specified IoTDomain
	 * 
	 * @param iotDomainOcid
	 * @return
	 */
	public List<DigitalTwinAdapter> listDigitalTwinAdapters(@NonNull String iotDomainOcid) {
		return listDigitalTwinAdapters(iotDomainOcid, null);

	}

	/**
	 * get a list of all DigitalTwinAdapter in the specified IoTDomainSummary, if
	 * displayName is not null it's used to limit the results
	 * 
	 * @param iotDomainSummary
	 * @param displayName
	 * @return
	 */
	public List<DigitalTwinAdapter> listDigitalTwinAdapters(@NonNull IotDomainSummary iotDomainSummary,
			String displayName) {
		return listDigitalTwinAdapters(iotDomainSummary.getId(), displayName);
	}

	/**
	 * get a list of all DigitalTwinModel in the specified IoTDomain, if displayName
	 * is not null it's used to limit the results
	 * 
	 * @param iotDomain
	 * @param displayName
	 * @return
	 */
	public List<DigitalTwinAdapter> listDigitalTwinAdapters(@NonNull IotDomain iotDomain, String displayName) {
		return listDigitalTwinAdapters(iotDomain.getId(), displayName);

	}

	/**
	 * get a list of all DigitalTwinModel in the specified IoTDomain, if displayName
	 * is not null it's used to limit the results
	 * 
	 * @param iotDomainOcid
	 * @param displayName
	 * @return
	 */
	public List<DigitalTwinAdapter> listDigitalTwinAdapters(@NonNull String iotDomainOcid, String displayName) {
		List<DigitalTwinAdapterSummary> modelSummaries = listDigitalTwinAdapterSummaries(iotDomainOcid, displayName);
		return modelSummaries.stream().map(ms -> getDigitalTwinAdapter(ms)).toList();
	}

	/**
	 * get the DigitalTwinAdapter in the specified IotDomain with the displayName,
	 * returns null of there are no matches
	 * 
	 * @param iotDomainSummary
	 * @param displayName
	 * @return
	 */
	public DigitalTwinAdapter getDigitalTwinAdapter(@NonNull IotDomainSummary iotDomainSummary,
			@NonNull String displayName) {
		return getDigitalTwinAdapter(iotDomainSummary.getId(), displayName);
	}

	/**
	 * get the DigitalTwinAdapter in the specified IotDomain with the displayName,
	 * returns null of there are no matches
	 * 
	 * @param iotDomain
	 * @param displayName
	 * @return
	 */
	public DigitalTwinAdapter getDigitalTwinAdapter(@NonNull IotDomain iotDomain, @NonNull String displayName) {
		return getDigitalTwinAdapter(iotDomain.getId(), displayName);

	}

	/**
	 * get the DigitalTwinAdapter in the specified IotDomain with the displayName,
	 * returns null of there are no matches
	 * 
	 * @param iotDomainOcid
	 * @param displayName
	 * @return
	 */
	public DigitalTwinAdapter getDigitalTwinAdapter(@NonNull String iotDomainOcid, @NonNull String displayName) {
		List<DigitalTwinAdapterSummary> digitalTwinAdapterSummaries = listDigitalTwinAdapterSummaries(iotDomainOcid,
				displayName);
		if (digitalTwinAdapterSummaries.isEmpty()) {
			return null;
		}
		return getDigitalTwinAdapter(digitalTwinAdapterSummaries.getFirst());
	}

	/**
	 * get the DigitalTwinAdapter from the specified summary
	 * 
	 * @param digitalTwinAdapterSummary
	 * @return
	 */
	public DigitalTwinAdapter getDigitalTwinAdapter(@NonNull DigitalTwinAdapterSummary digitalTwinAdapterSummary) {
		GetDigitalTwinAdapterResponse resp = iotClient.getDigitalTwinAdapter(
				GetDigitalTwinAdapterRequest.builder().digitalTwinAdapterId(digitalTwinAdapterSummary.getId()).build());
		return resp.getDigitalTwinAdapter();
	}

	/**
	 * get all DigitalTwinInstanceSummary in the specified IotDomain
	 * 
	 * @param iotDomainSummary
	 * @return
	 */
	public List<DigitalTwinInstanceSummary> listDigitalTwinInstanceSummaries(
			@NonNull IotDomainSummary iotDomainSummary) {
		return listDigitalTwinInstanceSummaries(iotDomainSummary.getId());
	}

	/**
	 * get all DigitalTwinInstanceSummary in the specified IotDomain
	 * 
	 * @param iotDomain
	 * @return
	 */
	public List<DigitalTwinInstanceSummary> listDigitalTwinInstanceSummaries(@NonNull IotDomain iotDomain) {
		return listDigitalTwinInstanceSummaries(iotDomain.getId());

	}

	/**
	 * get all DigitalTwinInstanceSummary in the specified IotDomain
	 * 
	 * @param iotDomainOcid
	 * @return
	 */
	public List<DigitalTwinInstanceSummary> listDigitalTwinInstanceSummaries(@NonNull String iotDomainOcid) {
		return listDigitalTwinInstanceSummaries(iotDomainOcid, null);

	}

	/**
	 * get all DigitalTwinInstanceSummary in the specified IotDomain, if displayName
	 * is non null limits to only results with that displayName
	 * 
	 * @param iotDomainSummary
	 * @return
	 */
	public List<DigitalTwinInstanceSummary> listDigitalTwinInstanceSummaries(@NonNull IotDomainSummary iotDomainSummary,
			String displayName) {
		return listDigitalTwinInstanceSummaries(iotDomainSummary.getId(), displayName);
	}

	/**
	 * get all DigitalTwinInstanceSummary in the specified IotDomain, if displayName
	 * is non null limits to only results with that displayName
	 * 
	 * @param iotDomain
	 * @return
	 */
	public List<DigitalTwinInstanceSummary> listDigitalTwinInstanceSummaries(@NonNull IotDomain iotDomain,
			String displayName) {
		return listDigitalTwinInstanceSummaries(iotDomain.getId(), displayName);

	}

	/**
	 * get all DigitalTwinInstanceSummary in the specified IotDomain, if displayName
	 * is non null limits to only results with that displayName
	 * 
	 * @param iotDomainOcid
	 * @return
	 */
	public List<DigitalTwinInstanceSummary> listDigitalTwinInstanceSummaries(@NonNull String iotDomainOcid,
			String displayName) {
		ListDigitalTwinInstancesRequest.Builder requestBuilder = ListDigitalTwinInstancesRequest.builder()
				.iotDomainId(iotDomainOcid);
		if (displayName != null) {
			requestBuilder.displayName(displayName);
		}
		Iterable<DigitalTwinInstanceSummary> digitalTwinInstanceSummaries = iotClient.getPaginators()
				.listDigitalTwinInstancesRecordIterator(requestBuilder.build());
		return StreamSupport.stream(digitalTwinInstanceSummaries.spliterator(), false).toList();
	}

	/**
	 * get a list of all DigitalTwinInstance in the specified IoTDomainSummary
	 * 
	 * @param iotDomainSummary
	 * @return
	 */
	public List<DigitalTwinInstance> listDigitalTwinInstances(@NonNull IotDomainSummary iotDomainSummary) {
		return listDigitalTwinInstances(iotDomainSummary.getId());
	}

	/**
	 * get a list of all DigitalTwinInstance in the specified IoTDomain
	 * 
	 * @param iotDomain
	 * @return
	 */
	public List<DigitalTwinInstance> listDigitalTwinInstances(@NonNull IotDomain iotDomain) {
		return listDigitalTwinInstances(iotDomain.getId());

	}

	/**
	 * get a list of all DigitalTwinInstance in the specified IoTDomain
	 * 
	 * @param iotDomainOcid
	 * @return
	 */
	public List<DigitalTwinInstance> listDigitalTwinInstances(@NonNull String iotDomainOcid) {
		return listDigitalTwinInstances(iotDomainOcid, null);

	}

	/**
	 * get a list of all DigitalTwinInstance in the specified IoTDomainSummary, if
	 * displayName is not null it's used to limit the results
	 * 
	 * @param iotDomainSummary
	 * @param displayName
	 * @return
	 */
	public List<DigitalTwinInstance> listDigitalTwinInstances(@NonNull IotDomainSummary iotDomainSummary,
			String displayName) {
		return listDigitalTwinInstances(iotDomainSummary.getId(), displayName);
	}

	/**
	 * get a list of all DigitalTwinInstance in the specified IoTDomain, if
	 * displayName is not null it's used to limit the results
	 * 
	 * @param iotDomain
	 * @param displayName
	 * @return
	 */
	public List<DigitalTwinInstance> listDigitalTwinInstances(@NonNull IotDomain iotDomain, String displayName) {
		return listDigitalTwinInstances(iotDomain.getId(), displayName);

	}

	/**
	 * get a list of all DigitalTwinInstanceSummary in the specified IoTDomain, if
	 * displayName is not null it's used to limit the results
	 * 
	 * @param iotDomainOcid
	 * @param displayName
	 * @return
	 */
	public List<DigitalTwinInstance> listDigitalTwinInstances(@NonNull String iotDomainOcid, String displayName) {
		List<DigitalTwinInstanceSummary> modelSummaries = listDigitalTwinInstanceSummaries(iotDomainOcid, displayName);
		return modelSummaries.stream().map(ms -> getDigitalTwinInstance(ms)).toList();
	}

	/**
	 * get the DigitalTwinInstance in the specified IotDomain with the displayName,
	 * returns null of there are no matches
	 * 
	 * @param iotDomainSummary
	 * @param displayName
	 * @return
	 */
	public DigitalTwinInstance getDigitalTwinInstance(@NonNull IotDomainSummary iotDomainSummary,
			@NonNull String displayName) {
		return getDigitalTwinInstance(iotDomainSummary.getId(), displayName);
	}

	/**
	 * get the DigitalTwinInstance in the specified IotDomain with the displayName,
	 * returns null of there are no matches
	 * 
	 * @param iotDomain
	 * @param displayName
	 * @return
	 */
	public DigitalTwinInstance getDigitalTwinInstance(@NonNull IotDomain iotDomain, @NonNull String displayName) {
		return getDigitalTwinInstance(iotDomain.getId(), displayName);

	}

	/**
	 * get the DigitalTwinAdapter in the specified IotDomain with the displayName,
	 * returns null of there are no matches
	 * 
	 * @param iotDomainOcid
	 * @param displayName
	 * @return
	 */
	public DigitalTwinInstance getDigitalTwinInstance(@NonNull String iotDomainOcid, @NonNull String displayName) {
		List<DigitalTwinInstanceSummary> digitalTwinInstanceSummaries = listDigitalTwinInstanceSummaries(iotDomainOcid,
				displayName);
		if (digitalTwinInstanceSummaries.isEmpty()) {
			return null;
		}
		return getDigitalTwinInstance(digitalTwinInstanceSummaries.getFirst());
	}

	/**
	 * get the DigitalTwinInstance from the specified summary
	 * 
	 * @param digitalTwinAdapterSummary
	 * @return
	 */
	public DigitalTwinInstance getDigitalTwinInstance(@NonNull DigitalTwinInstanceSummary digitalTwinInstanceSummary) {
		GetDigitalTwinInstanceResponse resp = iotClient.getDigitalTwinInstance(GetDigitalTwinInstanceRequest.builder()
				.digitalTwinInstanceId(digitalTwinInstanceSummary.getId()).build());
		return resp.getDigitalTwinInstance();
	}

	/**
	 * gets a map of name / value pairs for the latest data for the specified
	 * DigitalTwinInstance. Note that it's up to the caller to know what type of
	 * data each key represents and to convert that into an appropriate object
	 * 
	 * @param digitalTwinInstanceSummary
	 * @return
	 */
	public Map<String, Object> getDigitalTwinInstanceContent(
			@NonNull DigitalTwinInstanceSummary digitalTwinInstanceSummary) {
		return getDigitalTwinInstanceContent(digitalTwinInstanceSummary.getId());
	}

	/**
	 * gets a map of name / value pairs for the latest data for the specified
	 * DigitalTwinInstance. Note that it's up to the caller to know what type of
	 * data each key represents and to convert that into an appropriate object
	 * 
	 * @param digitalTwinInstance
	 * @return
	 */
	public Map<String, Object> getDigitalTwinInstanceContent(@NonNull DigitalTwinInstance digitalTwinInstance) {
		return getDigitalTwinInstanceContent(digitalTwinInstance.getId());
	}

	/**
	 * gets a map of name / value pairs for the latest data for the specified
	 * DigitalTwinInstance. Note that it's up to the caller to know what type of
	 * data each key represents and to convert that into an appropriate object
	 * 
	 * @param digitalTwinInstanceOcid
	 * @return
	 */
	public Map<String, Object> getDigitalTwinInstanceContent(@NonNull String digitalTwinInstanceOcid) {
		GetDigitalTwinInstanceContentRequest request = GetDigitalTwinInstanceContentRequest.builder()
				.digitalTwinInstanceId(digitalTwinInstanceOcid).build();
		GetDigitalTwinInstanceContentResponse resp = iotClient.getDigitalTwinInstanceContent(request);
		return resp.getMap();
	}

	public DigitalTwinInstance createDigitalTwinInstance(@NonNull IotDomain iotDomain, @NonNull String displayName,
			@NonNull Secret vaultSecret, String externalKey, String description, DigitalTwinModel digitalTwinModel,
			DigitalTwinAdapter digitalTwinAdapter) {
		return createDigitalTwinInstance(iotDomain.getId(), displayName, vaultSecret.getId(), externalKey, description,
				(digitalTwinModel == null ? null : digitalTwinModel.getId()),
				(digitalTwinAdapter == null ? null : digitalTwinAdapter.getId()));
	}

	public DigitalTwinInstance createDigitalTwinInstance(@NonNull String iotDomainOcid, @NonNull String displayName,
			@NonNull String authOcid, String externalKey, String description, String digitalTwinModelOcid,
			String digitalTwinAdapterOcid) {
		CreateDigitalTwinInstanceDetails.Builder detailsBuilder = CreateDigitalTwinInstanceDetails.builder()
				.iotDomainId(iotDomainOcid).displayName(displayName).authId(authOcid);
		if (externalKey != null) {
			detailsBuilder.externalKey(externalKey);
		}
		if (description != null) {
			detailsBuilder.description(description);
		}
		if (digitalTwinModelOcid != null) {
			detailsBuilder.digitalTwinModelId(digitalTwinModelOcid);
		}
		if (digitalTwinAdapterOcid != null) {
			detailsBuilder.digitalTwinAdapterId(digitalTwinAdapterOcid);
		}
		CreateDigitalTwinInstanceRequest request = CreateDigitalTwinInstanceRequest.builder()
				.createDigitalTwinInstanceDetails(detailsBuilder.build()).build();
		CreateDigitalTwinInstanceResponse resp = iotClient.createDigitalTwinInstance(request);
		return resp.getDigitalTwinInstance();
	}

	public boolean deleteDigitalTwinInstance(@NonNull DigitalTwinInstanceSummary digitalTwinInstanceSummary) {
		return deleteDigitalTwinInstance(digitalTwinInstanceSummary.getId());
	}

	public boolean deleteDigitalTwinInstance(@NonNull DigitalTwinInstance digitalTwinInstance) {
		return deleteDigitalTwinInstance(digitalTwinInstance.getId());
	}

	private boolean deleteDigitalTwinInstance(String digitalTwinInstanceOcid) {
		DeleteDigitalTwinInstanceRequest request = DeleteDigitalTwinInstanceRequest.builder()
				.digitalTwinInstanceId(digitalTwinInstanceOcid).build();
		DeleteDigitalTwinInstanceResponse resp = iotClient.deleteDigitalTwinInstance(request);
		return resp.get__httpStatusCode__() == HttpStatus.SC_OK;
	}

}
