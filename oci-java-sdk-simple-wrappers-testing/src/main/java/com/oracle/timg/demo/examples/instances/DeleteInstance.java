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
package com.oracle.timg.demo.examples.instances;

import java.util.List;
import java.util.stream.Collectors;

import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.model.InternetGateway;
import com.oracle.bmc.core.model.VnicAttachment;
import com.oracle.bmc.identity.model.AvailabilityDomain;
import com.oracle.bmc.identity.model.Compartment;
import com.oracle.timg.oci.authentication.AuthenticationProcessor;
import com.oracle.timg.oci.compute.ComputeProcessor;
import com.oracle.timg.oci.identity.IdentityProcessor;
import com.oracle.timg.oci.networking.VCNProcessor;

import lombok.extern.slf4j.Slf4j;
import timgutilities.textio.ChoiceDescription;
import timgutilities.textio.ChoiceDescriptionData;
import timgutilities.textio.TextIOUtils;

@Slf4j
public class DeleteInstance {

	public final static void main(String args[]) throws IllegalArgumentException, Exception {
		String confAuthName = TextIOUtils.getString("What's the oci config file section to use ?", "DEFAULT");
		AuthenticationProcessor ap = new AuthenticationProcessor(confAuthName);
		// look for a compartment
		log.info("Gathering basic information");
		String compartmentName = TextIOUtils.getString("What compartment name for these resources ?", "demoinstance");
		IdentityProcessor ip = new IdentityProcessor(ap);
		Compartment c = ip.locateCompartment(compartmentName);
		if (c == null) {
			log.info("Cannot locate compartment " + compartmentName + " unable to proceed");
			return;
		}

		log.info("Gathering instance information");

		// Get the AD
		List<AvailabilityDomain> ads = ip.getAvailabilityDomains(c);
		ChoiceDescriptionData<AvailabilityDomain> adChoices = new ChoiceDescriptionData<>(
				ads.stream().map(ad -> new ChoiceDescription<>(ad.getName(), ad)).collect(Collectors.toList()));
		AvailabilityDomain ad = TextIOUtils
				.getParamChoice("Please chose the availability domain your instance is running in", adChoices);
		log.info("Using AD " + ad.getName());

		String instanceName = TextIOUtils.getString("What is the name of your instance ?", "demoinstance");
		ComputeProcessor cp = new ComputeProcessor(ap);
		Instance i = cp.locateInstance(instanceName, c, ad);
		if (i == null) {
			log.info("Unable to locate an existing instance called " + instanceName + " in AD " + ad.getName()
					+ " in Compartment " + c.getName());
		}

		List<VnicAttachment> vnicAttachments = cp.getInstanceVnicAttachements(i).stream().filter(
				vnicAttachment -> vnicAttachment.getLifecycleState().equals(VnicAttachment.LifecycleState.Attached))
				.toList();
		List<String> subNetOcids = vnicAttachments.stream().map(vnicAttachment -> vnicAttachment.getSubnetId())
				.distinct().toList();
		List<String> vcnOcids = vnicAttachments.stream().map(vnicAttachment -> vnicAttachment.getSubnetId()).distinct()
				.toList();

		log.info("Instance is connected to subnets " + subNetOcids);
		log.info("Instance is connected to vcns " + vcnOcids);

		boolean ableToDoDeletion = true;
		String subnetOCIDToDelete = null;
		if (subNetOcids.size() == 0) {
			log.warn("There are no subnets, cannot actually delete anything, was instance " + instanceName
					+ " setup under this script ?");
		} else if (subNetOcids.size() == 1) {
			subnetOCIDToDelete = subNetOcids.get(0);
			log.info("Single subnet located");
		} else {
			log.warn(
					"There are multiple subnets, cannot actually delete anything, will retrieve details based on the first subnet, was instance "
							+ instanceName + " setup under this script ?");
			subnetOCIDToDelete = subNetOcids.get(0);
			ableToDoDeletion = false;
		}
		String vcnOcidToDelete = null;
		if (vcnOcids.size() == 0) {
			log.warn("There are no vcns, cannot actually delete anything, was instance " + instanceName
					+ " setup under this script ?");
		} else if (vcnOcids.size() == 1) {
			vcnOcidToDelete = vcnOcids.get(0);
			log.info("Single vcn located");
		} else {
			log.warn(
					"There are multiple vcns, cannot actually delete anything, will retrieve details based on the first vcn, was instance "
							+ instanceName + " setup under this script?");
			vcnOcidToDelete = vcnOcids.get(0);
			ableToDoDeletion = false;
		}

		VCNProcessor vp = new VCNProcessor(ap);
		List<InternetGateway> gateways = vp.listInternetGateways(c.getId(), vcnOcidToDelete);

	}
}
