package com.willwinder.universalgcodesender;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.willwinder.universalgcodesender.i18n.Localization;
import com.willwinder.universalgcodesender.listeners.ControllerStatus;
import com.willwinder.universalgcodesender.mockobjects.MockConnection;
import com.willwinder.universalgcodesender.mockobjects.MockMarlinCommunicator;
import com.willwinder.universalgcodesender.types.GcodeCommand;
import com.willwinder.universalgcodesender.utils.GUIHelpers;
import com.willwinder.universalgcodesender.utils.GcodeStreamTest;
import com.willwinder.universalgcodesender.utils.Settings;
import com.willwinder.universalgcodesender.utils.SimpleGcodeStreamReader;

public class MarlinControllerTest {
	private MockMarlinCommunicator mgc;
	private static File tempDir;
	private Settings settings = new Settings();

	public MarlinControllerTest() {
	}

	@BeforeClass
	static public void setup() throws IOException {
		tempDir = GcodeStreamTest.createTempDirectory();
	}

	@AfterClass
	static public void teardown() throws IOException {
		FileUtils.forceDeleteOnExit(tempDir);
	}

	@Before
	public void setUp() throws Exception {
		this.mgc = new MockMarlinCommunicator();

		// Initialize private variable.
		Field f = GUIHelpers.class.getDeclaredField("unitTestMode");
		f.setAccessible(true);
		f.set(null, true);
		Localization.initialize("en_US");
	}

	@After
	public void tearDown() throws Exception {
		// Initialize private variable.
		Field f = GUIHelpers.class.getDeclaredField("unitTestMode");
		f.setAccessible(true);
		f.set(null, false);
	}

	private Settings getSettings() {
		return settings;
	}

	@Test
	public void testGetFirmwareVersion() throws Exception {
		System.out.println("getFirmwareVersion");
		MarlinController instance = new MarlinController(mgc);
		String result;
		String expResult;

		expResult = "<Not connected>";
		result = instance.getFirmwareVersion();
		assertEquals(expResult, result);

		instance.openCommPort(getSettings().getConnectionDriver(), "blah", 1234);
		expResult = "Marlin 2.0.6.1";
		instance.rawResponseHandler("echo:start");
		instance.rawResponseHandler(expResult);
		instance.rawResponseHandler("Marlin Second Message Ignored");
		result = instance.getFirmwareVersion();
		assertEquals(expResult, result);
		instance.closeCommPort();

		instance.openCommPort(getSettings().getConnectionDriver(), "blah", 1234);
		expResult = "Marlin 2.0.x";
		instance.rawResponseHandler("start");
		instance.rawResponseHandler("echo:" + expResult);
		instance.rawResponseHandler("echo:Marlin Second Message Ignored");
		result = instance.getFirmwareVersion();
		assertEquals(expResult, result);
		instance.closeCommPort();
	}

	/**
	 * Test of numOpenCommPortCalls method, of class MarlinController.
	 */
	@Test
	public void testOpenCommPort() {
		System.out.println("openCommPort/isCommOpen");
		String port = "serialPort";
		int portRate = 12345;
		MarlinController instance = new MarlinController(mgc);
		Boolean expResult = true;
		Boolean result = false;
		try {
			result = instance.openCommPort(getSettings().getConnectionDriver(), port, portRate);
		} catch (Exception e) {
			fail("Unexpected exception from GrblController: " + e.getMessage());
		}
		assertEquals(expResult, result);
		assertEquals(expResult, instance.isCommOpen());
		assertEquals(port, mgc.portName);
		assertEquals(portRate, mgc.portRate);

		String exception = "";
		// Check exception trying to open the comm port twice.
		try {
			instance.openCommPort(getSettings().getConnectionDriver(), port, portRate);
		} catch (Exception e) {
			exception = e.getMessage();
		}
		assertEquals("Comm port is already open.", exception);
	}

	/**
	 * Test of numCloseCommPortCalls method, of class MarlinController.
	 */
	@Test
	public void testCloseCommPort() {
		System.out.println("closeCommPort/isCommOpen");
		MarlinController instance = new MarlinController(mgc);

		// Make sure comm is closed
		assertEquals(false, instance.isCommOpen());

		Boolean result = false;
		try {
			// Test closing while already closed.
			result = instance.closeCommPort();
			assertEquals(true, result);
			assertEquals(false, instance.isCommOpen());

			// Test closed after opening thenc losing.
			instance.openCommPort(getSettings().getConnectionDriver(), "blah", 1234);
			result = instance.closeCommPort();
		} catch (Exception e) {
			fail("Unexpected exception from GrblController: " + e.getMessage());
		}
		assertEquals(true, result);
		assertEquals(false, instance.isCommOpen());
	}

	private void assertCounts(MarlinController instance, int total, int sent, int remaining) {
		assertEquals(total, instance.rowsInSend());
		assertEquals(sent, instance.rowsSent());
		assertEquals(remaining, instance.rowsRemaining());
	}

	/**
	 * Test of rowsInSend method, of class MarlinController.
	 */
	@Test
	public void testRowsAsteriskMethods() throws Exception {
		System.out.println("testRowsAsteriskMethods");
		MarlinController instance = new MarlinController(mgc);
		instance.openCommPort(getSettings().getConnectionDriver(), "blah", 1234);

		// Test 1.
		// When not sending, no commands queues, everything should be zero.
		assertCounts(instance, 0, 0, 0);

		// Add 30 commands.
		List<GcodeCommand> commands = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			commands.add(new GcodeCommand("G0X" + i));
		}
		instance.queueStream(new SimpleGcodeStreamReader(commands));

		try {
			// instance.openCommPort("blah", 123);
			instance.beginStreaming();
			mgc.areActiveCommands = true;
		} catch (Exception ex) {
			fail("Unexpected exception from MarlinController: " + ex.getMessage());
		}

		// Test 2.
		// 30 Commands queued, zero sent, 30 completed.
		assertCounts(instance, 30, 0, 30);

		// Test 3.
		// Sent 15 of them, none completed.
		try {
			for (int i = 0; i < 15; i++) {
				GcodeCommand command = new GcodeCommand("G0 X1");
				command.setSent(true);
				command.setResponse("ok");
				instance.commandSent(command);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			fail("Unexpected exception from command sent: " + ex.getMessage());
		}
		assertCounts(instance, 30, 15, 30);

		// Test 4.
		// Complete 15 of them.
		try {
			for (int i = 0; i < 15; i++) {
				GcodeCommand command = new GcodeCommand("G0X1"); // Whitespace removed.
				command.setSent(true);
				command.setResponse("ok");
				instance.commandComplete(command.getCommandString());
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			fail("Unexpected exception from command complete: " + ex.getMessage());
		}
		assertCounts(instance, 30, 15, 15);

		// Test 5.
		// Finish sending/completing the remaining 15 commands.
		try {
			for (int i = 0; i < 15; i++) {
				GcodeCommand command = new GcodeCommand("G0 X1");
				command.setSent(true);
				command.setResponse("ok");
				instance.commandSent(command);
				instance.commandComplete(command.getCommandString());
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			fail("Unexpected exception from command complete: " + ex.getMessage());
		}
		mgc.areActiveCommands = false;
		assertCounts(instance, 30, 30, 0);
	}

	/**
	 * Test of numQueueStringForCommCalls method, of class GrblController.
	 */
	@Test
	public void testSendCommandImmediately() throws Exception {
		System.out.println("queueStringForComm");
		String str = "G0 X0 ";
		MarlinController instance = new MarlinController(mgc);
		instance.openCommPort(getSettings().getConnectionDriver(), "blah", 123);
		instance.rawResponseHandler("Marlin bugfix-2.0.x");
		assertEquals(0, mgc.numQueueStringForCommCalls);
		assertEquals(0, mgc.numStreamCommandsCalls);
		GcodeCommand command = instance.createCommand(str);
		instance.sendCommandImmediately(command);
		assertEquals(1, mgc.numQueueStringForCommCalls);
		assertEquals(1, mgc.numStreamCommandsCalls);
		assertEquals(str, mgc.queuedString);
	}

	/**
	 * Test of pauseStreaming method, of class MarlinController.
	 */
	@Test
	public void testPauseStreaming() throws Exception {
		System.out.println("pauseStreaming");
		MarlinController instance = new MarlinController(mgc);
		instance.openCommPort(getSettings().getConnectionDriver(), "blah", 1234);

		assertFalse(instance.isPaused());
		instance.pauseStreaming();
		assertEquals(1, mgc.numPauseSendCalls);
		assertEquals(1, mgc.queuedStrings.size());
		assertEquals("M0", mgc.queuedStrings.get(0));

		assertFalse(instance.isPaused());
		instance.rawResponseHandler("echo:busy: paused for user");
		assertTrue(instance.isPaused());
	}

	/**
	 * Test of M0 handling
	 */
	@Test
	public void testPauseStreamingViaM0() throws Exception {
		System.out.println("pauseStreamingViaM0");
		MarlinController instance = new MarlinController(mgc);
		instance.openCommPort(getSettings().getConnectionDriver(), "blah", 1234);

		assertFalse(instance.isPaused());
		instance.sendCommandImmediately(instance.createCommand("M0"));
		assertEquals(1, mgc.queuedStrings.size());
		assertEquals("M0", mgc.queuedStrings.get(0));

		assertFalse(instance.isPaused());
		instance.rawResponseHandler("echo:busy: paused for user");
		assertTrue(instance.isPaused());
	}

	/**
	 * Test of resumeStreaming method, of class MarlinController.
	 */
	@Test
	public void testResumeStreaming() throws Exception {
		System.out.println("resumeStreaming");
		MarlinController instance = new MarlinController(mgc);
		instance.openCommPort(getSettings().getConnectionDriver(), "blah", 1234);

		instance.rawResponseHandler("echo:busy: paused for user");
		assertTrue(instance.isPaused());
		instance.resumeStreaming();
		assertEquals(2, mgc.numResumeSendCalls); // MarlinController calls it twice to cope with some corner cases

		String sentstr = mgc.baos.toString();
		assertEquals("M108\n", sentstr);

		assertTrue(instance.isPaused());
		instance.rawResponseHandler("ok");
		assertFalse(instance.isPaused());
	}

	/**
	 * Test of status string parsing.
	 */
	@Test
	public void testStatusStringParsing() throws Exception {
		System.out.println("Status String Parsing");
		MarlinController instance = new MarlinController(mgc);
		instance.openCommPort(getSettings().getConnectionDriver(), "blah", 1234);
		instance.rawResponseHandler("X:1.23 Y:4.56 Z:7.89 E:0.00 Count X:0 Y:0 Z:0");

		ControllerStatus controllerStatus = instance.getControllerStatus();
		assertEquals(1.23, controllerStatus.getMachineCoord().getX(), 0.001);
		assertEquals(4.56, controllerStatus.getMachineCoord().getY(), 0.001);
		assertEquals(7.89, controllerStatus.getMachineCoord().getZ(), 0.001);
	}

	/**
	 * Test of rel time status string parsing.
	 */
	@Test
	public void testRealtimeStatusStringParsing() throws Exception {
		System.out.println("Realtime Status String Parsing");
		MarlinController instance = new MarlinController(mgc);
		instance.openCommPort(getSettings().getConnectionDriver(), "blah", 1234);
		instance.rawResponseHandler("<<X:1.11 Y:2.22 Z:3.33 E:0.00 F:25.00 S_XYZ:3>>");

		ControllerStatus controllerStatus = instance.getControllerStatus();
		assertEquals(1.11, controllerStatus.getMachineCoord().getX(), 0.001);
		assertEquals(2.22, controllerStatus.getMachineCoord().getY(), 0.001);
		assertEquals(3.33, controllerStatus.getMachineCoord().getZ(), 0.001);
	}
}
