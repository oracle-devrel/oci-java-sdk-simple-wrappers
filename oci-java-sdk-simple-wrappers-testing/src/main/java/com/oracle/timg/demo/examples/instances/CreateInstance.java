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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import com.oracle.bmc.core.model.Image;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.model.InternetGateway;
import com.oracle.bmc.core.model.Shape;
import com.oracle.bmc.core.model.Subnet;
import com.oracle.bmc.core.model.Vcn;
import com.oracle.bmc.core.model.Vnic;
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
import timgutilities.textio.TextIOUtils.NUM_TYPE;

@Slf4j
public class CreateInstance {

	private static final String OPERATING_SYSTEM = "Oracle Linux";

	public final static void main(String args[]) throws IllegalArgumentException, Exception {
		String confAuthName = TextIOUtils.getString("What's the oci config file section to use ?", "LONDON");
		AuthenticationProcessor ap = new AuthenticationProcessor(confAuthName);
		// look for a compartment
		log.info("Gathering basic information");
		IdentityProcessor ip = new IdentityProcessor(ap);

		String homeRegion = ip.getHomeRegion();
		String configRegion = ap.getConfigFileRegionName();
		List<String> regions = new LinkedList<>(ip.listRegions(true, configRegion));
		log.info("Region from config is " + configRegion);
		log.info("Home region is " + homeRegion);
		log.info("Other regions are " + regions);
		regions.add(0, homeRegion);
		regions.add(0, configRegion);

		String workingRegion = TextIOUtils
				.getStringChoice(
						"Please chose a region to operate in, your config file region is " + configRegion
								+ " your home region is " + homeRegion + " - only subscribed regions are listed",
						regions);

		if (!homeRegion.equals(workingRegion)) {
			ap.setRegionName(workingRegion);
			ip.setRegion(workingRegion);
		}

		String parentCompartmentOCID;
		boolean useTenancyRootForParent = TextIOUtils.getYN("Use tenancy root as the parent compartment ?", false);
		// allow one level of navigation down
		if (useTenancyRootForParent) {
			parentCompartmentOCID = ap.getTenancyOCID();
		} else {
			List<Compartment> childCompartments = ip.listChildCompartments();
			if (childCompartments.size() == 0) {
				log.info(
						"No child compartments of tenancy root to use as the sub compartment, will use tenancy root as parent of child compartment");
				parentCompartmentOCID = ap.getTenancyOCID();
			} else {
				ChoiceDescriptionData<Compartment> compartmentsChoices = new ChoiceDescriptionData<>(
						childCompartments.stream().map(c -> new ChoiceDescription<>(c.getName(), c)).toList());
				compartmentsChoices.setDoSort(true);
				compartmentsChoices.addAbandonOption("Revert to using tenancy for parent compartment", false);
				Compartment child = TextIOUtils.getParamChoice(
						"Please chose the compartment to use as the parent ofthe compartment you will create",
						compartmentsChoices);
				if (child == null) {
					log.info("OK, using tenancy root as parent compartment");
					parentCompartmentOCID = ap.getTenancyOCID();
				} else {
					parentCompartmentOCID = child.getId();
					log.info("OK, using " + child.getName() + " with OCID " + parentCompartmentOCID
							+ " as the parent of the new compartment ");
				}
			}
		}
		String compartmentName = TextIOUtils.getString("What compartment name for these resources ?", "demoinstance");
		Compartment c = ip.locateCompartment(compartmentName, parentCompartmentOCID);
		if (c == null) {
			log.info("Cannot locate compartment " + compartmentName + " going to try create it");
			String compartmentDescription = TextIOUtils.getString(
					"What description do you want to use for the compartment ?", compartmentName + " compartment");
			c = ip.createCompartment(compartmentName, compartmentDescription, parentCompartmentOCID);
		}
		log.info("Compartment OCID is " + c.getId() + "named " + c.getName() + " it's parent is "
				+ c.getCompartmentId());

		log.info("Capturing data on the networking");
		// try to create the VCN for this compartment
		String vcnName = TextIOUtils.getString("What name do you want to use for the VCN ?", "demoinstance");
		VCNProcessor vp = new VCNProcessor(ap);
		Vcn vcn = vp.locateVCN(vcnName, c);
		if (vcn == null) {
			log.info("Cannot locate VCN named " + vcnName + " goibng to try to create it");
			String vcnCidr = TextIOUtils.getString("What CIDR do you want to use for the VCN ?", "10.0.0.0/16");
			vcn = vp.createVcn(vcnName, vcnCidr, c);
		}
		log.info("VCN OCID is " + vcn.getId() + " in compartment ocid " + vcn.getCompartmentId());

		String internetGwName = TextIOUtils.getString("What name do you want to use for the internet gateway ?",
				"demoinstance");
		InternetGateway gateway = vp.locateInternetGateway(internetGwName, c, vcn);
		if (gateway == null) {
			log.info("Cannot locate internet gateway  named " + internetGwName + " going to create it");
			gateway = vp.createInternetGateway(internetGwName, c, vcn);
		}

		log.info("Internet gateway OCID is " + gateway.getId() + " in compartment ocid " + vcn.getCompartmentId());
		vp.addInternetGatewayToDefaultRouteTable(vcn, gateway);

		String subnetName = TextIOUtils.getString("What name do you want to use for the subnet ?", "demoinstance");
		Subnet s = vp.locateSubnet(subnetName, c, vcn);
		if (s == null) {
			log.info("Cannot locate subnet named " + subnetName + " going to try and create it");
			String subnetCidr = TextIOUtils.getString("What CIDR do you want to use for the subnet ?", "10.0.0.0/24");
			Boolean privateOnly = TextIOUtils.getYN("Does this subnet block public internet facing instances ?", false);
			s = vp.createSubnet(subnetName, subnetCidr, vcn, c, privateOnly);
		}
		log.info("Subnet ODIC is " + s.getId() + " in compartment ocid " + vcn.getCompartmentId());

		// now the compute client work itself

		log.info("Capturing data on the compute instance");
		String instanceName = TextIOUtils.getString("What name do you want to use for the compute instance name ?",
				"demoinstance");
		ComputeProcessor cp = new ComputeProcessor(ap);
		// Get the AD
		List<AvailabilityDomain> ads = ip.getAvailabilityDomains(c);
		ChoiceDescriptionData<AvailabilityDomain> adChoices = new ChoiceDescriptionData<>(
				ads.stream().map(ad -> new ChoiceDescription<>(ad.getName(), ad)).collect(Collectors.toList()));
		adChoices.setDoSort(true);
		AvailabilityDomain ad = TextIOUtils.getParamChoice("Please chose the availability domain for your instance",
				adChoices);
		log.info("Using AD " + ad.getName());

		Instance i = cp.locateInstance(instanceName, c, ad);
		if (i == null) {
			log.info("Unable to locate an existing instance called " + instanceName + " in AD " + ad.getName()
					+ " in Compartment " + c.getName() + " Creating it");

			List<Shape> shapes = cp.getVmShapes(ad, c);
			ChoiceDescriptionData<Shape> shapeChoices = new ChoiceDescriptionData<>(shapes.stream()
					.map(shape -> new ChoiceDescription<>(shape.getShape(), shape)).collect(Collectors.toList()));
			shapeChoices.setDoSort(true);
			Shape shape = TextIOUtils.getParamChoice("Please chose the shape to create your instance", shapeChoices);
			log.info("Using Shape " + shape.getShape());

			// flex shapes need more info
			int ocpuCount = 0;
			float memCount = 0;
			if (shape.getIsFlexible()) {
				log.info("You have chosen a flexible shape, additional, information is needed");
				float gbuPerOcpu = shape.getMemoryOptions().getDefaultPerOcpuInGBs();
				int minOcpu = shape.getOcpuOptions().getMin().intValue();
				int maxOcpu = shape.getOcpuOptions().getMax().intValue();
				ocpuCount = TextIOUtils.getInt("How many OCPUs do you want, this script will allocate " + gbuPerOcpu
						+ " GB of memory per OCPU selected", NUM_TYPE.RANGE, minOcpu, maxOcpu, 4);
				memCount = gbuPerOcpu * ocpuCount;
				log.info("Will create an instance with " + ocpuCount + " OCPUs and " + memCount + "GB memory");
			}

			List<Image> images = cp.getImages(shape, c, OPERATING_SYSTEM);
			ChoiceDescriptionData<Image> imageChoices = new ChoiceDescriptionData<>(images.stream()
					.map(image -> new ChoiceDescription<>(image.getDisplayName(), image)).collect(Collectors.toList()));
			imageChoices.setDoSort(true);
			Image image = TextIOUtils.getParamChoice("Please chose the image to create your instance", imageChoices);
			log.info("Using Image " + image.getDisplayName());

			String sshPubKeyFile = TextIOUtils.getFileMatching(
					"Please enter the location of the ssh public key file (must end in .pub)", "pub$",
					"/Users/tg13456/Desktop/OCI Settings/default/sshkey.pub");
			String sshKey = Files.readString(Paths.get(sshPubKeyFile));
			log.info("Loaded public ssh key from " + sshPubKeyFile);

			log.info("Creating instance, this may take a short time");

			if (shape.getIsFlexible()) {
				i = cp.createInstance(instanceName, c, ad, shape, image, s, sshKey, null, ocpuCount, memCount);
			} else {
				i = cp.createInstance(instanceName, c, ad, shape, image, s, sshKey, null);
			}

			log.info("Created instance " + i.getId());

		}
		log.info("Instance Public IPs");
		List<VnicAttachment> vnicAttachments = cp.getInstanceVnicAttachements(i);

		vnicAttachments.forEach(vnicAttachment -> {
			Vnic vnic = vp.getVnicFromAttachement(vnicAttachment);
			log.info(vnic.getPublicIp());
		});

		log.info("Instance details\n" + cp.printInstance(i, vp.getClient()));
	}
}
