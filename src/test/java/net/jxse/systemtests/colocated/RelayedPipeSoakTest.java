package net.jxse.systemtests.colocated;

import net.jxse.systemtests.colocated.configs.PeerConfigurator;
import net.jxta.platform.NetworkManager;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Set of tests which send messages across longer periods of time than most
 * other tests, in an attempt to detect any issues which only creep in after
 * sustained use.
 * 
 * Similarly to {@link RelayedCommsTest}, these tests cannot be run together
 * due to relay isolation issues which should be investigated further.
 */
@Ignore("broken due to peer isolation issues")
public class RelayedPipeSoakTest {

	public static final int NUM_MESSAGES = 10000;
	public static final int MESSAGE_SIZE = 1024 * 32;
	
	@Rule
	public TemporaryFolder tempStorage = new TemporaryFolder();
	
	private NetworkManager relayManager;
	private NetworkManager aliceManager;
	private NetworkManager bobManager;
	
	@Test
	public void testTcp() throws Exception {
		relayManager = PeerConfigurator.createTcpRdvRelayPeer("relay", 50000, tempStorage);
		aliceManager = PeerConfigurator.createTcpClientPeer("alice", relayManager, tempStorage);
		bobManager = PeerConfigurator.createTcpClientPeer("bob", relayManager, tempStorage);
		startPeers();
		SoakTester.soakTestSinglePipe(aliceManager, bobManager, NUM_MESSAGES, MESSAGE_SIZE);
	}

	@Test
	public void testHttp2() throws Exception {
		relayManager = PeerConfigurator.createHttp2RdvRelayPeer("relay", 50000, tempStorage);
		aliceManager = PeerConfigurator.createHttp2ClientPeer("alice", relayManager, tempStorage);
		bobManager = PeerConfigurator.createHttp2ClientPeer("bob", relayManager, tempStorage);
		startPeers();
		SoakTester.soakTestSinglePipe(aliceManager, bobManager, NUM_MESSAGES, MESSAGE_SIZE);
	}
	
	@Test
	public void testHttp() throws Exception {
		relayManager = PeerConfigurator.createHttp2RdvRelayPeer("relay", 50000, tempStorage);
		aliceManager = PeerConfigurator.createHttpClientPeer("alice", relayManager, tempStorage);
		bobManager = PeerConfigurator.createHttpClientPeer("bob", relayManager, tempStorage);
		startPeers();
		SoakTester.soakTestSinglePipe(aliceManager, bobManager, NUM_MESSAGES, MESSAGE_SIZE);
	}
	
	private void startPeers() throws Exception {
		relayManager.startNetwork();
		aliceManager.startNetwork();
		bobManager.startNetwork();
		
		Thread.sleep(5000);
	}
	
	@After
	public void killRelay() {
		relayManager.stopNetwork();
	}
	
	@After
	public void killAlice() {
		aliceManager.stopNetwork();
	}
	
	@After
	public void killBob() {
		bobManager.stopNetwork();
	}
	
}
