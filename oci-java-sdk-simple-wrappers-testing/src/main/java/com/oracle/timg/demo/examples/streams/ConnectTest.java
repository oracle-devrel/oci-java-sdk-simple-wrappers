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
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.json.JSONObject;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.streaming.StreamClient;
import com.oracle.bmc.streaming.model.PutMessagesDetails;
import com.oracle.bmc.streaming.model.PutMessagesDetails.Builder;
import com.oracle.bmc.streaming.model.PutMessagesDetailsEntry;
import com.oracle.bmc.streaming.requests.PutMessagesRequest;
import com.oracle.bmc.streaming.responses.PutMessagesResponse;

public class ConnectTest {
	// public final static String STREAM_NAME =
	// "ocid1.stream.oc1.phx.aaaaaaaaztwq3ocpjbu37zuaborcaeth2aw7ufcwrhlze2nzrrkwpw2nlpja";
	public final static String STREAM_NAME = "TimG";

	public static void main(String[] args) {

		String messageKey = "timeStream";
		// build some JSON to do stuff with
		JSONObject jsonObject = new JSONObject();
		// have the ID be based on the clock. not briliant but for now given we're only
		// doing one message per invocation it's going to be pretty unique.
		jsonObject.put("id", System.currentTimeMillis());
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss.SSSS");
		jsonObject.put("timestamp", sdf.format(new Date()));
		jsonObject.put("message", "Hello World!");
		String messageValue = jsonObject.toString(4);
		// create an authentication provider, use the default file location
		// $HOME/.oci/config and specify I wand to use the DEFAULT config.
		AuthenticationDetailsProvider provider;
		try {
			provider = new ConfigFileAuthenticationDetailsProvider("API_USER");
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		StreamClient streamClient = StreamClient.builder().build(provider);
		// Build the message details, for now this is broken into segments to make it
		// easier
		// the builder is independent of the actual stream
		Builder builder = PutMessagesDetails.builder();
		// the builder can operate on many messages at a time, but we only have one
		PutMessagesDetailsEntry message;
		try {
			message = PutMessagesDetailsEntry.builder().key(messageKey.getBytes("UTF-8"))
					.value(messageValue.getBytes("UTF-8")).build();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return;
		}
		// messages are added in a list
		List<PutMessagesDetailsEntry> messagesList = Arrays.asList(message);
		// build the mesages structure
		PutMessagesDetails putMessagesDetails = builder.messages(messagesList).build();
		// now need to create a request to send the messages
		PutMessagesRequest putMessagesRequest = PutMessagesRequest.builder()
				.streamId("ocid1.stream.oc1.phx.aaaaaaaaztwq3ocpjbu37zuaborcaeth2aw7ufcwrhlze2nzrrkwpw2nlpja")
				.putMessagesDetails(putMessagesDetails).build();
		// Finally get the client to send the request
		System.out.println("Sending message " + putMessagesRequest);
		PutMessagesResponse messagesResponse = streamClient.putMessages(putMessagesRequest);
		System.out.println("Got response " + messagesResponse
				.toString());/*
								 * // let's find out what happened String responseId =
								 * messagesResponse.getOpcRequestId() ; PutMessagesResult putMessagesResult =
								 * messagesResponse.getPutMessagesResult() ; int failuresCount =
								 * putMessagesResult.getFailures() ; List<PutMessagesResultEntry>
								 * messagesResults = putMessagesResult.getEntries() ;
								 * System.out.println("Send message "+);
								 */

	}

}
