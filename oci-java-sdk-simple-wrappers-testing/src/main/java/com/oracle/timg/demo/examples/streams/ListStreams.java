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
package com.oracle.timg.demo.examples.streams;

import java.io.IOException;
import java.util.stream.Collectors;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.streaming.StreamAdminClient;
import com.oracle.bmc.streaming.requests.ListStreamsRequest;
import com.oracle.bmc.streaming.responses.ListStreamsResponse;

public class ListStreams {

	public static void main(String[] args) {
		// create an authentication provider, use the default file location
		// $HOME/.oci/config and specify I wand to use the DEFAULT config.
		AuthenticationDetailsProvider provider;
		try {
			provider = new ConfigFileAuthenticationDetailsProvider("API_USER");
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		StreamAdminClient streamAdminClient = StreamAdminClient.builder().build(provider);
		ListStreamsRequest request = ListStreamsRequest.builder()
				.compartmentId("ocid1.compartment.oc1..aaaaaaaavxkbsxpb3j7iml4rxe4hbnjzagu6xmcjnbmctg32ptqvldmhx6yq")
				.build();
		ListStreamsResponse listResponse = streamAdminClient.listStreams(request);
		String streams = listResponse.getItems().stream().map(resp -> resp.toString()).collect(Collectors.joining(","));
		System.out.println("Resp = " + streams);
	}

}
