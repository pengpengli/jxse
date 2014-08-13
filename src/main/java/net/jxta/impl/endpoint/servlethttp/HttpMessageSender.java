/*
 * Copyright (c) 2001-2007 Sun Microsystems, Inc.  All rights reserved.
 *
 *  The Sun Project JXTA(TM) Software License
 *
 *  Redistribution and use in source and binary forms, with or without 
 *  modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice, 
 *     this list of conditions and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution.
 *
 *  3. The end-user documentation included with the redistribution, if any, must 
 *     include the following acknowledgment: "This product includes software 
 *     developed by Sun Microsystems, Inc. for JXTA(TM) technology." 
 *     Alternately, this acknowledgment may appear in the software itself, if 
 *     and wherever such third-party acknowledgments normally appear.
 *
 *  4. The names "Sun", "Sun Microsystems, Inc.", "JXTA" and "Project JXTA" must 
 *     not be used to endorse or promote products derived from this software 
 *     without prior written permission. For written permission, please contact 
 *     Project JXTA at http://www.jxta.org.
 *
 *  5. Products derived from this software may not be called "JXTA", nor may 
 *     "JXTA" appear in their name, without prior written permission of Sun.
 *
 *  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 *  INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND 
 *  FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL SUN 
 *  MICROSYSTEMS OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, 
 *  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING 
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, 
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  JXTA is a registered trademark of Sun Microsystems, Inc. in the United 
 *  States and other countries.
 *
 *  Please see the license information page at :
 *  <http://www.jxta.org/project/www/license.html> for instructions on use of 
 *  the license in source files.
 *
 *  ====================================================================
 *
 *  This software consists of voluntary contributions made by many individuals 
 *  on behalf of Project JXTA. For more information on Project JXTA, please see 
 *  http://www.jxta.org.
 *
 *  This license is based on the BSD license adopted by the Apache Foundation. 
 */
package net.jxta.impl.endpoint.servlethttp;

import net.jxta.endpoint.EndpointAddress;
import net.jxta.endpoint.EndpointService;
import net.jxta.endpoint.MessageSender;
import net.jxta.endpoint.Messenger;
import net.jxta.exception.PeerGroupException;
import net.jxta.logging.Logger;
import net.jxta.logging.Logging;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Simple Client MessageSender
 */
class HttpMessageSender implements MessageSender {

    private final static transient Logger LOG = Logging.getLogger(HttpMessageSender.class.getName());

    /**
     * The ServletHttpTransport that created this object
     */
    private final ServletHttpTransport servletHttpTransport;

    /**
     * The public address for this message sender
     */
    private final EndpointAddress publicAddress;

    /**
     *  The Set of active messengers. We keep track so that we can aggressively
     *  close the Messengers when the transport is shut down.
     */
    private final Map<HttpClientMessenger, Object> messengers = new WeakHashMap<HttpClientMessenger, Object>();

    /**
     * constructor
     */
    public HttpMessageSender(ServletHttpTransport servletHttpTransport, EndpointAddress publicAddress) throws PeerGroupException {

        this.servletHttpTransport = servletHttpTransport;
        this.publicAddress = publicAddress;

        if (Logging.SHOW_CONFIG && LOG.isConfigEnabled()) {
            StringBuilder configInfo = new StringBuilder( "Configuring HTTP Client Message Transport : " + servletHttpTransport.assignedID);
            configInfo.append("\n\tPublic Address = ").append(publicAddress);
            LOG.config(configInfo.toString());
        }

    }

    /**
     * {@inheritDoc}
     */
    public EndpointAddress getPublicAddress() {
        return publicAddress;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isConnectionOriented() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public boolean allowsRouting() {
        return true;
    }

    /**
     * shut down all client connections.
     */
    public synchronized void start() throws PeerGroupException {

        if (servletHttpTransport.getEndpointService().addMessageTransport(this) == null) {
            throw new PeerGroupException("Transport registration refused");
        }

        Logging.logCheckedInfo(LOG, "HTTP Client Transport started.");

    }

    /**
     * shut down all client connections.
     */
    public synchronized void stop() {
        synchronized (messengers) {
            Iterator<HttpClientMessenger> eachMessenger = messengers.keySet().iterator();

            while (eachMessenger.hasNext()) {
                HttpClientMessenger aMessenger = eachMessenger.next();
                aMessenger.closeImpl();
                eachMessenger.remove();
            }
        }

        Logging.logCheckedInfo(LOG, "HTTP Client Transport stopped.");

    }

    /**
     * {@inheritDoc}
     */
    public Messenger getMessenger(EndpointAddress destAddr) {
//    public Messenger getMessenger(EndpointAddress destAddr, Object hintIgnored) {

        Logging.logCheckedDebug(LOG, "getMessenger for : ", destAddr);

        if (!getProtocolName().equals(destAddr.getProtocolName())) {

            Logging.logCheckedWarning(LOG, "Cannot make messenger for protocol :", destAddr.getProtocolName());
            return null;

        }

        try {
            // Right now we do not want to "announce" outgoing messengers 
            // because they get pooled and so must not be grabbed by a listener.
            // If "announcing" is to be done, that should be by the endpoint
            // and probably with a subtly different interface.

            HttpClientMessenger result = new HttpClientMessenger(servletHttpTransport, publicAddress, destAddr);

            synchronized (messengers) {
                messengers.put(result, null);
            }

            return result;

        } catch (SocketTimeoutException noConnect) {

            Logging.logCheckedWarning(LOG, "Could not connect to ", destAddr, " : ", noConnect.getMessage());

        } catch (ConnectException noConnect) {

            Logging.logCheckedWarning(LOG, "Failed to connect to ", destAddr + " : ", noConnect.getMessage());

        } catch (Throwable e) {

            Logging.logCheckedWarning(LOG, "Could not make messenger for ", destAddr, "\n", e);

        }

        // If we got here, we failed.
        return null;
    }

    /**
     *  {@inheritDoc}
     */
    public String getProtocolName() {
        return servletHttpTransport.HTTP_PROTOCOL_NAME;
    }

    /**
     *  {@inheritDoc}
     */
    public EndpointService getEndpointService() {

        return servletHttpTransport.getEndpointService();

    }
}
