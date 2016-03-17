/*
 *  The Sun Project JXTA(TM) Software License
 *
 *  Copyright (c) 2001-2007 Sun Microsystems, Inc. All rights reserved.
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

 *  This software consists of voluntary contributions made by many individuals 
 *  on behalf of Project JXTA. For more information on Project JXTA, please see 
 *  http://www.jxta.org.
 *
 *  This license is based on the BSD license adopted by the Apache Foundation. 
 */

package net.jxta.impl.content.srdisocket;

import net.jxta.content.*;
import net.jxta.document.Advertisement;
import net.jxta.document.AdvertisementFactory;
import net.jxta.document.Document;
import net.jxta.document.MimeMediaType;
import net.jxta.id.ID;
import net.jxta.id.IDFactory;
import net.jxta.logging.Logger;
import net.jxta.logging.Logging;
import net.jxta.peergroup.PeerGroup;
import net.jxta.pipe.PipeID;
import net.jxta.pipe.PipeService;
import net.jxta.platform.Module;
import net.jxta.platform.ModuleSpecID;
import net.jxta.protocol.ContentShareAdvertisement;
import net.jxta.protocol.ModuleImplAdvertisement;
import net.jxta.protocol.PipeAdvertisement;
import net.jxta.socket.JxtaServerSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Reference implementation of the ContentProvider interface.  This
 * implementation works
 * as follows:
 * <ul>
 * <li>
 * Being the default/fallback implementation, this provider makes no
 * assumptions as to the data contained within the Content instance.
 * It may be static or dynamic content and may or may not be
 * different across multiple peers who are sharing the same Content ID.
 * Each client will be served using a single call to a
 * <code>Content</code>'s document stream.
 * </li>
 * <li>
 * Content instances are shared by calling the shareContent(Content)
 * method and then making another peer aware of the returned
 * ContentAdvertisement.  How the remote peer is made aware of the
 * ContentAdvertisement is of no concern.
 * </li>
 * <li>
 * The ContentAdvertisement passed to <code>retrieveContent</code>
 * is assumed to contain only the information required to rebuild
 * a Content instance in the presence of the data itself.
 * </li>
 * <li>
 * No validation is performed on the transferred data.  All validation
 * must be done out-of-band and post-transfer.
 * </li>
 * </ul>
 */
public class SRDISocketContentProvider
        implements ContentProviderSPI {

	private static final Logger LOG =
            Logging.getLogger(SRDISocketContentProvider.class.getName());

    /**
     * The number of milliseconds the accept loop will sleep when an
     * IOException prevents the creation of the server socket.
     */
    private static final int ACCEPT_RETRY_DELAY =
            Integer.getInteger(SRDISocketContentProvider.class.getName()
                    + ".acceptRetryDelay", 5 * 1000);

    /**
     * Module spec ID for this provider.
     */
    private static final String MODULE_SPEC_ID =
            "urn:jxta:uuid-AC3AA08FC4A14C15A78A88B4D4F87554"
                    + "A7A79198AC274BF38DDBA376EB9A788406";

    /**
     * Parsed and ready-to-use version of MODULE_SPEC_ID.
     */
    private static final ModuleSpecID specID;

    // Initialized at construction
    private final Map<ID, SRDIContentShare> shares =
            new HashMap<ID, SRDIContentShare>();
    private CopyOnWriteArrayList<ContentProviderListener> listeners =
            new CopyOnWriteArrayList<ContentProviderListener>();

    // Initialized by init
    private PeerGroup peerGroup;
    private ScheduledExecutorService executor;
    private PipeAdvertisement pipeAdv;

    // Initialized and managed by start/stop
    private boolean running = false;

    //////////////////////////////////////////////////////////////////////////
    // Inner classes:

    /**
     * Executor thread factory to configure reasonable thread names and
     * settings, etc..
     */
    private class ThreadFactoryImpl
            implements ThreadFactory, UncaughtExceptionHandler {
        private ThreadGroup threadGroup;

        public ThreadFactoryImpl(PeerGroup group) {
            StringBuilder name = new StringBuilder();
            name.append(group.getPeerGroupName());
            name.append(" - ");
            name.append(SRDISocketContentProvider.class.getName());
            name.append(" pool");

            threadGroup = new ThreadGroup(name.toString());
            threadGroup.setDaemon(true);
        }

        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(threadGroup, runnable);
            thread.setUncaughtExceptionHandler(this);
            return thread;
        }

        public void uncaughtException(Thread thread, Throwable throwable) {

            Logging.logCheckedError(LOG, "Uncaught throwable in pool thread: ",
                thread, "\n", throwable);

        }
    }

    /**
     * Proxy for clientExecution().
     */
    private class Client implements Runnable {
        private Socket socket;

        public Client(Socket clientSocket) {
            socket = clientSocket;
        }

        public void run() {
            clientExecution(socket);
        }
    }

    //////////////////////////////////////////////////////////////////////////
    // Constructors and initializers:

    /**
     * Static initializer.
     */
    static {
        try {
            URI specURI = new URI(MODULE_SPEC_ID);
            specID = (ModuleSpecID) IDFactory.fromURI(specURI);
        } catch (URISyntaxException urisx) {
            throw (new RuntimeException(
                    "Illegal ModuleSpecURI in code: " + MODULE_SPEC_ID,
                    urisx));
        }
    }

    //////////////////////////////////////////////////////////////////////////
    // ContentProviderSPI interface methods:

    /**
     * {@inheritDoc}
     */
        @Override
    public void init(PeerGroup group, ID assignedID, Advertisement implAdv) {
        Logging.logCheckedDebug(LOG, "initProvider(): group=", group);

        peerGroup = group;
        executor = Executors.newScheduledThreadPool(5, new ThreadFactoryImpl(group));

        pipeAdv = (PipeAdvertisement) AdvertisementFactory.newAdvertisement(PipeAdvertisement.getAdvertisementType());
        pipeAdv.setType(PipeService.UnicastType);

        PipeID pipeID = IDFactory.newPipeID(peerGroup.getPeerGroupID());
        pipeAdv.setPipeID(pipeID);
    }

    /**
     * {@inheritDoc}
     */
        @Override
    public synchronized int startApp(String[] args) {

        Logging.logCheckedDebug(LOG, "startApp()");

        if (running) {
            return Module.START_OK;
        }

        if (peerGroup.getPipeService() == null) {
            Logging.logCheckedWarning(LOG, "Stalled until there is a PipeService");
            return Module.START_AGAIN_STALLED;
        }

        running = true;

        // Start the accept loop
        executor.execute(new Runnable() {
            @Override
            public void run() {
                acceptExecution();
            }
        });

        return Module.START_OK;
    }

    /**
     * {@inheritDoc}
     */
        @Override
    public synchronized void stopApp() {

        Logging.logCheckedDebug(LOG, "stopApp()");

        if (!running) {
            return;
        }

        /*
         * XXX 20070911 mcumings: We really need to be able to abort all
         * ContentTransfer instances that we've created that are still
         * in-flight.  Right now the ContentTransfers will silently
         * fail if the ScheduledExecutorService is shutdown while the
         * transfer is in-flight.  I don't like the idea of maintaining
         * references to every ContentTransfer instance, but I also don't
         * like the idea of each instance using it's own dedicated thread.
         * Suggestions?
         */

        running = false;
        notifyAll();
    }

//    /**
//     * {@inheritDoc}
//     */
//    public ContentProviderSPI getInterface() {
//        return (ContentProviderSPI) ModuleWrapperFactory.newWrapper(
//                new Class[]{ContentProviderSPI.class},
//                this);
//    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Advertisement getImplAdvertisement() {
        ModuleImplAdvertisement moduleImplAdvertisement = (ModuleImplAdvertisement) AdvertisementFactory.newAdvertisement(ModuleImplAdvertisement.getAdvertisementType());
        moduleImplAdvertisement.setModuleSpecID(specID);
        moduleImplAdvertisement.setCode(getClass().getName());
        moduleImplAdvertisement.setProvider("https://jxta.dev.java.net/");
        moduleImplAdvertisement.setDescription("ContentProvider implementation using JxtaSockets");

        return moduleImplAdvertisement;
    }

    //////////////////////////////////////////////////////////////////////////
    // ContentProvider interface methods:

    /**
     * {@inheritDoc}
     */
        @Override
    public void addContentProviderListener(ContentProviderListener listener) {
        listeners.add(listener);
    }

    /**
     * {@inheritDoc}
     */
        @Override
    public void removeContentProviderListener(ContentProviderListener listener) {
        listeners.remove(listener);
    }

    /**
     * {@inheritDoc}
     */
        @Override
    public ContentTransfer retrieveContent(ContentID contentID) {

        Logging.logCheckedDebug(LOG, "retrieveContent(" + contentID + ")");

        synchronized (this) {
            if (!running) {
                return null;
            }
        }

        synchronized (shares) {
            ContentShare share = getShare(contentID);
            if (share != null) {
                return new NullContentTransfer(this, share.getContent());
            }
        }

        return new SRDISocketContentTransfer(this, executor, peerGroup, contentID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentTransfer retrieveContent(ContentShareAdvertisement adv) {

        Logging.logCheckedDebug(LOG, "retrieveContent(ContentShareAdvertisement)");

        synchronized (this) {
            if (!running) {
                return null;
            }
        }
        synchronized (shares) {
            ContentShare share = getShare(adv.getContentID());
            if (share != null) {
                return new NullContentTransfer(this, share.getContent());
            }
        }
        return new SRDISocketContentTransfer(this, executor, peerGroup, adv);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ContentShare> shareContent(Content content) {

        Logging.logCheckedDebug(LOG, "shareContent(): Content=", content);

        PipeAdvertisement pipeAdvertisement;
        synchronized (this) {
            if (pipeAdv == null) {
                Logging.logCheckedDebug(LOG, "Cannot create share before initialization");
                return null;
            }
            pipeAdvertisement = pipeAdv;
        }

        List<ContentShare> result = new ArrayList<>(1);
        ID id = content.getContentID();
        SRDIContentShare share;
        synchronized (shares) {
            share = getShare(id);
            if (share == null) {
                share = new SRDIContentShare(this, content, pipeAdvertisement);
                shares.put(id, share);
                result.add(share);
            }
        }

        if (result.isEmpty()) {
            /*
             * This content was already shared.  We'll skip notifying our
             * listeners but will return it in the results.
             */
            result.add(share);
        } else {
            fireContentShared(result);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean unshareContent(ContentID contentID) {

        Logging.logCheckedDebug(LOG, "unhareContent(): ContentID=", contentID);

        ContentShare oldShare;
        synchronized (shares) {
            oldShare = shares.remove(contentID);
        }
        if (oldShare == null) {
            return false;
        } else {
            fireContentUnshared(contentID);
            return true;
        }
    }

    /**
     * {@inheritDoc}
     */
        @Override
    public void findContentShares(int maxNum, ContentProviderListener listener) {
        List<ContentShare> shareList;

        synchronized (shares) {
            shareList = new ArrayList<>(Math.min(maxNum, shares.size()));
            for (ContentShare share : shares.values()) {
                if (shareList.size() >= maxNum) {
                    break;
                }
                shareList.add(share);
            }
        }

        listener.contentSharesFound(
                new ContentProviderEvent.Builder(this, shareList)
                    .lastRecord(true)
                    .build());
    }

    //////////////////////////////////////////////////////////////////////////
    // Package methods:

    /**
     * Returns the peer peerGroup the service is running in.
     * 
     * @return PeerGroup instance this service is running in
     */
    protected PeerGroup getPeerGroup() {
        return peerGroup;
    }

    //////////////////////////////////////////////////////////////////////////
    // Private methods:

    /**
     * Server execution mainline.
     */
    private void acceptExecution() {

        Logging.logCheckedDebug(LOG, "Acceptor thread starting");

        JxtaServerSocket serverSocket = null;

        try {
            while (true) {
                synchronized (this) {
                    if (!running) {
                        break;
                    }
                }

                try {

                    if (serverSocket == null) {
                        LOG.debug("Creating new server socket");
                        serverSocket = new JxtaServerSocket(peerGroup, pipeAdv);
                    }

                    // LOGGING: was Finer
                    Logging.logCheckedDebug(LOG, "Waiting to accept client...");

                    Socket socket = serverSocket.accept();

                    if (socket != null) {
                        Logging.logCheckedDebug(LOG, "Incoming socket connection");
                        executor.execute(new Client(socket));
                    }

                } catch (SocketTimeoutException socktox) {

                    // LOGGING: was Finest
                    Logging.logCheckedDebug(LOG, "Socket timed out");

                } catch (IOException iox) {

                    Logging.logCheckedError(LOG, "Caught exception in acceptor loop\n", iox);

                    // Close and deref the current socket
                    try {
                        serverSocket.close();
                    } catch (IOException iox2) {
                        LOG.warn("Could not close socket\n", iox);
                    } finally {
                        serverSocket = null;
                    }

                    // Wait a while before the next attempt
                    try {

                        Thread.sleep(ACCEPT_RETRY_DELAY);

                    } catch (InterruptedException intx) {

                        Logging.logCheckedError(LOG, "Interrupted\n", intx);

                    }

                } catch (RuntimeException rtx) {
                    LOG.warn("Caught runtime exception\n", rtx);
                    throw(rtx);
                }
            }
        } finally {
            LOG.info("Exiting");
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException iox) {
                    LOG.warn("Could not close socket\n", iox);
                }
            }
        }

        Logging.logCheckedDebug(LOG, "Accceptor thread exiting");

    }

    /**
     * Per-client server execution mainline.
     */
    private void clientExecution(Socket socket) {

        SocketAddress remote = socket.getRemoteSocketAddress();
        SRDIContentShare share = null;

        try {

            Logging.logCheckedDebug(LOG, "Client executing against socket: ", socket);

            InputStream inStream = socket.getInputStream();
            ContentRequest request = ContentRequest.readFromStream(inStream);

            ContentResponse response = new ContentResponse(request);
            share = getShare(request.getContentID());
            response.setSuccess(share != null);

            if (share != null) share.fireShareSessionOpened(remote);

            Logging.logCheckedDebug(LOG, "Client response being sent:\n",
                        response.getDocument(MimeMediaType.XMLUTF8));

            OutputStream outStream = socket.getOutputStream();
            response.writeToStream(outStream);

            if (response.getSuccess()) {

                // Send the content data
                Logging.logCheckedDebug(LOG, "Client transfer starting");

                // Notify listeners of access by remote peer
                share.fireShareSessionAccessed(remote);

                Content content = share.getContent();
                Document contentDocument = content.getDocument();
                contentDocument.sendToStream(outStream);
                outStream.flush();
            }

            Logging.logCheckedDebug(LOG, "Client transaction completed");

        } catch (IOException iox) {

            Logging.logCheckedWarning(LOG, "Caught exception in client thread\n", iox);

        } catch (RuntimeException rtx) {

            Logging.logCheckedError(LOG, "Caught runtime exception\n", rtx);
            throw (rtx);

        } finally {

            if (share != null) {
                share.fireShareSessionClosed(remote);
            }

            try {
                socket.close();
            } catch (IOException ignore) {
                // LOGGING: was Finest
                Logging.logCheckedDebug(LOG, "Ignoring exception", ignore);
            }

        }
    }

    /**
     * Returns the content share entry for the specified ContentID.
     *
     * @return content share
     */
    private SRDIContentShare getShare(ID id) {
        synchronized (shares) {
            return shares.get(id);
        }
    }

    /**
     * Notify our listeners that the provided shares are being exposed.
     *
     * @param shares list of fresh shares
     */
    private void fireContentShared(List<ContentShare> shares) {
        ContentProviderEvent event = null;
        for (ContentProviderListener listener : listeners) {
            if (event == null) {
                event = new ContentProviderEvent.Builder(this, shares)
                        .build();
            }
            listener.contentShared(event);
        }
    }

    /**
     * Notify our listeners that the provided shares are that are no
     * longer being exposed.
     *
     * @param contentID ContentID of the content which is no longer
     *                  being shared
     */
    private void fireContentUnshared(ContentID contentID) {
        ContentProviderEvent event = null;
        for (ContentProviderListener listener : listeners) {
            if (event == null) {
                event = new ContentProviderEvent.Builder(this, contentID).build();
            }
            listener.contentUnshared(event);
        }
    }

}
