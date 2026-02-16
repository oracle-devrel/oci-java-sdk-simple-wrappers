/*Copyright (c) 2026 Oracle and/or its affiliates.

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
package com.oracle.timg.demo.examples.iot;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.oracle.bmc.identity.model.Compartment;
import com.oracle.bmc.iot.model.DigitalTwinAdapter;
import com.oracle.bmc.iot.model.DigitalTwinInstance;
import com.oracle.bmc.iot.model.DigitalTwinModel;
import com.oracle.bmc.iot.model.IotDomain;
import com.oracle.bmc.iot.model.IotDomainGroup;
import com.oracle.timg.oci.authentication.AuthenticationProcessor;
import com.oracle.timg.oci.identity.IdentityProcessor;
import com.oracle.timg.oci.iot.IoTProcessor;
import com.oracle.timg.oci.vault.VaultProcessor;

import timgutilities.textio.ChoiceDescription;
import timgutilities.textio.ChoiceDescriptionData;
import timgutilities.textio.TextIOUtils;

public class TestIoT {
	static AuthenticationProcessor authenticationProcessor;
	static IdentityProcessor identityProcessor;
	static IoTProcessor iotProcessor;
	static VaultProcessor vaultProcessor;

	public static void main(String[] args) throws Exception {
		TextIOUtils.doOutput("Configuring OCI connection");
		authenticationProcessor = new AuthenticationProcessor("DEFAULT");
		identityProcessor = new IdentityProcessor(authenticationProcessor);
		iotProcessor = new IoTProcessor(authenticationProcessor);
		vaultProcessor = new VaultProcessor(authenticationProcessor);

		String compartmentPath = TextIOUtils.getString("Please enter the compartment path to operate in",
				"/domain-specialists/tim.graves/iot");
		Compartment compartment = identityProcessor.locateCompartmentByPath(compartmentPath);
		if (compartment == null) {
			TextIOUtils.doOutput("Can't locate compartment " + compartmentPath + ", cannot continue");
			System.exit(0);
		}
		TextIOUtils.doOutput("OCID of " + compartmentPath + " is " + compartment.getId());

		List<IotDomainGroup> iotDomainGroups = iotProcessor.listIoTDomainGroupsInCompartment(compartment);
		if (iotDomainGroups.isEmpty()) {
			TextIOUtils.doOutput("No Iot Domain Groups found in compartment " + compartmentPath + ", cannot continue");
			return;
		}
		ChoiceDescriptionData<IotDomainGroup> iotDomainGroupCdd = new ChoiceDescriptionData<>(iotDomainGroups.stream()
				.map(iotDomainGroup -> new ChoiceDescription<IotDomainGroup>(iotDomainGroup.getDisplayName(),
						iotDomainGroup))
				.toList());
		IotDomainGroup iotDomainGroup = TextIOUtils.getParamChoice("Please chose the IoT domain group",
				iotDomainGroupCdd);
		TextIOUtils.doOutput("You chose IoT Domain Group " + iotDomainGroup.getDisplayName());

		List<IotDomain> iotDomains = iotProcessor.listIotDomainsInIotDomainGroup(iotDomainGroup);
		if (iotDomains.isEmpty()) {
			TextIOUtils.doOutput(
					"No Iot Domains found in IotDomainGroup " + iotDomainGroup.getDisplayName() + ", cannot continue");
			return;
		}
		ChoiceDescriptionData<IotDomain> iotDomainCdd = new ChoiceDescriptionData<>(iotDomains.stream()
				.map(iotDomain -> new ChoiceDescription<IotDomain>(iotDomain.getDisplayName(), iotDomain)).toList());
		IotDomain iotDomain = TextIOUtils.getParamChoice("Please chose the IoT domain group", iotDomainCdd);
		TextIOUtils.doOutput("You chose IoT Domain " + iotDomain.getDisplayName());

		List<DigitalTwinModel> digitalTwinModels = iotProcessor.listDigitalTwinModels(iotDomain);
		DigitalTwinModel digitalTwinModel = null;
		if (digitalTwinModels.isEmpty()) {
			TextIOUtils.doOutput("No Digital Twin Models found in IotDomain  " + iotDomain.getDisplayName());
		} else {
			ChoiceDescriptionData<DigitalTwinModel> digitalTwinModelCdd = new ChoiceDescriptionData<>(digitalTwinModels
					.stream().map(dtm -> new ChoiceDescription<DigitalTwinModel>(dtm.getDisplayName(), dtm)).toList());
			digitalTwinModel = TextIOUtils.getParamChoice("Please chose the IoT domain group", digitalTwinModelCdd);
			TextIOUtils.doOutput("You chose Digital Twin Model " + digitalTwinModel.getDisplayName());
		}

		List<DigitalTwinAdapter> digitalTwinAdapters = iotProcessor.listDigitalTwinAdapters(iotDomain);
		DigitalTwinAdapter digitalTwinAdapter = null;
		if (digitalTwinAdapters.isEmpty()) {
			TextIOUtils.doOutput("No Digital Twin Adapters found in IotDomain  " + iotDomain.getDisplayName());
		} else {
			ChoiceDescriptionData<DigitalTwinAdapter> digitalTwinAdaptorsCdd = new ChoiceDescriptionData<>(
					digitalTwinAdapters.stream()
							.map(dta -> new ChoiceDescription<DigitalTwinAdapter>(dta.getDisplayName(), dta)).toList());
			digitalTwinAdapter = TextIOUtils.getParamChoice("Please chose the IoT domain group",
					digitalTwinAdaptorsCdd);
			TextIOUtils.doOutput("You chose Digital Twin Adapter " + digitalTwinAdapter.getDisplayName());
		}

		List<DigitalTwinInstance> digitalTwinInstances = iotProcessor.listDigitalTwinInstances(iotDomain);
		DigitalTwinInstance digitalTwinInstance = null;
		if (digitalTwinInstances.isEmpty()) {
			TextIOUtils.doOutput("No Digital Twin Instances found in IotDomain  " + iotDomain.getDisplayName());
		} else {
			ChoiceDescriptionData<DigitalTwinInstance> digitalTwinInstancesCdd = new ChoiceDescriptionData<>(
					digitalTwinInstances.stream()
							.map(dti -> new ChoiceDescription<DigitalTwinInstance>(dti.getDisplayName(), dti))
							.toList());
			digitalTwinInstance = TextIOUtils.getParamChoice("Please chose the IoT domain group",
					digitalTwinInstancesCdd);
			TextIOUtils.doOutput("You chose Digital Twin Instance " + digitalTwinInstance.getDisplayName());
		}

		if (digitalTwinInstance != null) {
			getAndPrintLatestInstanceData(digitalTwinInstance);
		}
		DigitalTwinInstance newDigitalTwinInstance = null;
		if ((digitalTwinInstance != null) && (digitalTwinAdapter != null) && (digitalTwinModel != null)) {
			if (TextIOUtils.getYN(
					"Do you want to create a digital twin instance using the model, adaptor, and auth detials of the digital twin you just chose (model="
							+ digitalTwinModel.getDisplayName() + ", adaptor=" + digitalTwinAdapter.getDisplayName()
							+ ")",
					false)) {
				String displayName = TextIOUtils.getString("Please enter the display name (alpha numeric only)");
				String description = TextIOUtils.getString("Please enter the description or empty string for none", "");
				if (description.length() == 0) {
					description = null;
				}
				String externalKey = TextIOUtils
						.getString("Please enter the external key, or empty string for a system assigned one", "");
				if (externalKey.length() == 0) {
					externalKey = null;
				}
				String authOcid = digitalTwinInstance.getAuthId();
				newDigitalTwinInstance = iotProcessor.createDigitalTwinInstance(iotDomain.getId(), displayName,
						authOcid, externalKey, description, digitalTwinModel.getId(), digitalTwinAdapter.getId());
				TextIOUtils.doOutput("DigitalTwinInstance just created is " + newDigitalTwinInstance);
				getAndPrintLatestInstanceData(newDigitalTwinInstance);
			}
		}
		if ((newDigitalTwinInstance != null)
				&& (TextIOUtils.getYN("Do you want to send some test data to this instance ?", true))) {
			sendTestDataInstance(iotDomain, newDigitalTwinInstance);
			getAndPrintLatestInstanceData(newDigitalTwinInstance);
		}
		if (newDigitalTwinInstance != null) {
			if (TextIOUtils.getYN("Do you want to delete the digital twin instance you just created ?", true)) {
				iotProcessor.deleteDigitalTwinInstance(newDigitalTwinInstance);
			}
		}
	}

	private static void sendTestDataInstance(IotDomain iotDomain, DigitalTwinInstance digitalTwinInstance)
			throws IOException {
		// send data, similar to this
		// curl -i -X POST -u "$DEVICE_EXTERNAL_KEY:$DEVICE_SECRET"
		// https://$IOT_DOMAIN_HOST/home/sonnenstatus/$DEVICE_ID -H
		// "Content-Type:application/json" -d
		// '{"consumptionAvgWattsLastMinute":488,"consumptionWattsPointInTime":517,"currentBatteryCapacityPercentage":25,"currentBatteryCapacitySystemPercentage":30,"gridConsumptionWattsPointInTime":44,"operatingMode":10,"remainingBatteryCapacityWattHours":4775,"reservedBatteryCapacityPercentage":5,"solarProductionWattsPointInTime":0,"time":1764004104225,"timestamp":"2025-11-24T17:08:24.225022Z[Europe/London]","batteryCharging":false,"batteryDischarging":true}'

		String externalKey = digitalTwinInstance.getExternalKey();
		String deviceSecret = vaultProcessor.getSecretContents(digitalTwinInstance.getAuthId());
		String iotDomainHost = iotDomain.getDeviceHost();
		String devicePath = TextIOUtils.getString("Please enter the absolute (startes with /) path on the host "
				+ iotDomainHost + " to send date to (this may need to include the device details)");
		String jsonPayload = TextIOUtils.getString("Please enter the data to send (this should be in JSON format");
		String url = "https://" + iotDomainHost + devicePath;
		String auth = externalKey + ":" + deviceSecret;
		TextIOUtils.doOutput("Sending to url " + url);
		TextIOUtils.doOutput("With credentials " + auth);
		String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
		String authHeader = "Basic " + encodedAuth;
		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Content-Type", "application/json")
				.header("Authorization", authHeader).POST(HttpRequest.BodyPublishers.ofString(jsonPayload)).build();

		try {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			TextIOUtils.doOutput("Status Code: " + response.statusCode());
			TextIOUtils.doOutput("Response Body: " + response.body());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void getAndPrintLatestInstanceData(DigitalTwinInstance digitalTwinInstance) {
		Map<String, Object> latestData = iotProcessor.getDigitalTwinInstanceContent(digitalTwinInstance);
		String resp = latestData.entrySet().stream().map(e -> e.getKey() + " - " + e.getValue().toString())
				.collect(Collectors.joining("\n"));
		TextIOUtils.doOutput("Most recent data for instance is\n" + resp);
	}
}
