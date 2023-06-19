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

package com.oracle.timg.oci.authentication;

import java.io.IOException;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * This class provides a wrapper round the OCI authentication mechanisms
 */

public class AuthenticationProcessor {

	private final ConfigFileAuthenticationDetailsProvider provider;
	@Getter
	@Setter
	private String regionName;
	@Getter
	private String configFileRegionName;

	/**
	 * Creates a processor looking for the specific configuration section name in
	 * the .oci config file using the region specified in that section
	 * 
	 * @param providerName - must not be null
	 * @throws IllegalArgumentException
	 * @throws IOException
	 */
	public AuthenticationProcessor(@NonNull String providerName) throws IllegalArgumentException, IOException {
		this(providerName, null);
	}

	/**
	 * Creates a processor looking for the specific configuration section name in
	 * the .oci config file using the region specified
	 * 
	 * @param providerName - must not be null
	 * @prram regionName - if null will use the default region in the confguration
	 *        file.
	 * @throws IllegalArgumentException
	 * @throws IOException
	 */
	public AuthenticationProcessor(@NonNull String providerName, String regionName)
			throws IllegalArgumentException, IOException {
		// get and save the provider away
		provider = new ConfigFileAuthenticationDetailsProvider(providerName);
		if (regionName != null) {
			this.regionName = regionName;
		} else {
			this.regionName = provider.getRegion().getRegionId();
		}
	}

	/**
	 * Provides a way to get the the underlying provider form the oci sdk, used for
	 * tasks that this class does not support
	 * 
	 * @return
	 * @throws IllegalArgumentException
	 */
	public AuthenticationDetailsProvider getProvider() throws IllegalArgumentException {
		return provider;
	}

	/**
	 * way to get the tenancy OCID - needed for the root compartment amongst other
	 * things
	 * 
	 * @return
	 */
	public String getTenancyOCID() {
		return provider.getTenantId();
	}
}
