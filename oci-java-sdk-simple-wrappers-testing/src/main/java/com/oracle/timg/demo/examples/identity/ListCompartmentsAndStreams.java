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
package com.oracle.timg.demo.examples.identity;

import java.io.IOException;
import java.util.stream.Collectors;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.identity.Identity;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.model.Compartment;
import com.oracle.bmc.identity.model.Compartment.LifecycleState;
import com.oracle.bmc.identity.requests.ListCompartmentsRequest;
import com.oracle.bmc.identity.responses.ListCompartmentsResponse;
import com.oracle.bmc.streaming.StreamAdminClient;
import com.oracle.bmc.streaming.requests.ListStreamsRequest;
import com.oracle.bmc.streaming.responses.ListStreamsResponse;

import lombok.extern.slf4j.Slf4j;

//Have Lombok create a logger for us
@Slf4j
public class ListCompartmentsAndStreams {

	public static void main(String[] args) {
		AuthenticationDetailsProvider provider;
		try {
			provider = new ConfigFileAuthenticationDetailsProvider("DEFAULT");
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		StreamAdminClient streamAdminClient = new StreamAdminClient(provider);
		String compartmentId = provider.getTenantId();
		try (Identity identityClient = new IdentityClient(provider);) {
			System.out.println("ListCompartments: with compartmentIdInSubtree == true");
			String nextPageToken = null;
			do {
				ListCompartmentsResponse response = identityClient
						.listCompartments(ListCompartmentsRequest.builder().limit(50).compartmentId(compartmentId)
								.compartmentIdInSubtree(Boolean.TRUE).page(nextPageToken).build());

				for (Compartment compartment : response.getItems()) {
					LifecycleState state = compartment.getLifecycleState();
					if (state == LifecycleState.Active) {
						System.out.println(compartment);
						listStreamsInCompartment(compartment.getId(), streamAdminClient);
					}
				}
				nextPageToken = response.getOpcNextPage();
			} while (nextPageToken != null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void listStreamsInCompartment(String id, StreamAdminClient streamAdminClient) {
		ListStreamsRequest request = ListStreamsRequest.builder().compartmentId(id).build();

		ListStreamsResponse listResponse = streamAdminClient.listStreams(request);
		String streams = listResponse.getItems().stream().map(resp -> resp.toString()).collect(Collectors.joining(","));
		System.out.println("Resp = " + streams);
	}

}
