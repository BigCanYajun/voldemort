/*
 * Copyright 2013 LinkedIn, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package voldemort.rest.coordinator.admin;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.*;

import voldemort.rest.RestErrorHandler;
import voldemort.rest.coordinator.config.StoreClientConfigService;

import java.io.IOException;

public class CoordinatorAdminRequestHandler extends SimpleChannelHandler {

    public HttpRequest request;
    private boolean readingChunks;
    private final Logger logger = Logger.getLogger(CoordinatorAdminRequestHandler.class);

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent messageEvent) throws Exception {
        logger.info("messageReceived!! (omg)");
        //sendResponse(messageEvent);
        if (!readingChunks) {
            HttpRequest request = this.request = (HttpRequest) messageEvent.getMessage();
            String requestURI = this.request.getUri();
            if (logger.isDebugEnabled()) {
                logger.debug("Request URI: " + requestURI);
            }

            if (request.isChunked()) {
                readingChunks = true;
            } else {
                HttpResponse response;
                // Instantiate the appropriate error handler
                HttpMethod httpMethod = request.getMethod();
                if (httpMethod.equals(HttpMethod.GET)) {
                    if(logger.isDebugEnabled()) {
                        logger.debug("Received a Http GET request at " + System.currentTimeMillis() + " ms");
                    }
                    response = handleGet();
                } else if (httpMethod.equals(HttpMethod.POST)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Recieved a Http POST request at " + System.currentTimeMillis() + " ms");
                    }
                    response = handlePut();
                } else if (httpMethod.equals(HttpMethod.DELETE)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Received a Http DELETE request at " + System.currentTimeMillis() + " ms");
                    }
                    response = handleDelete();
                } else {
                    String errorMessage = "Illegal Http request received at " + System.currentTimeMillis() + " ms";
                    logger.error(errorMessage);
                    RestErrorHandler.writeErrorResponse(messageEvent, BAD_REQUEST, errorMessage);
                    return;
                }
                messageEvent.getChannel().write(response);
            }
        } else {
            HttpChunk chunk = (HttpChunk) messageEvent.getMessage();
            if (chunk.isLast()) {
                readingChunks = false;
            }
        }
    }

    private HttpResponse handleGet() {
        String configFileContent = StoreClientConfigService.getAllConfigs();

        return sendResponse(OK, configFileContent);
    }

    private HttpResponse handlePut() {
        return sendResponse(NOT_FOUND, "GOT A PUT");
    }

    private HttpResponse handleDelete() {
        return sendResponse(BAD_REQUEST, "GOT A DELETE OMG OMG");
    }

    public HttpResponse sendResponse(HttpResponseStatus responseCode, String responseBody) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, responseCode);
        response.setHeader(CONTENT_LENGTH, responseBody.length());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(responseBody.getBytes());
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        ChannelBuffer responseContent = ChannelBuffers.dynamicBuffer();
        responseContent.writeBytes(outputStream.toByteArray());
        response.setContent(responseContent);
        logger.info("Sent " + response);
        return response;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        e.getCause().printStackTrace();
        e.getChannel().close();
    }
}
