package com.willwinder.universalgcodesender;

import java.util.logging.Logger;

import com.willwinder.universalgcodesender.listeners.ControllerState;
import com.willwinder.universalgcodesender.model.UGSEvent.ControlState;
import com.willwinder.universalgcodesender.types.GcodeCommand;

public class MarlinRealtimeController extends MarlinController {

	public MarlinRealtimeController() {
		this(new MarlinCommunicator());
	}

	public MarlinRealtimeController(MarlinCommunicator comm) {
		super(comm);
		capabilities.addCapability(GrblCapabilitiesConstants.REAL_TIME);
	}

	private static final Logger logger = Logger.getLogger(MarlinRealtimeController.class.getName());

	@Override
	public void requestStatusReport() throws Exception {
		if (!this.isCommOpen()) {
			throw new RuntimeException("Not connected to the controller");
		}

		comm.sendByteImmediately(GrblUtils.GRBL_STATUS_COMMAND);
	}

	@Override
	protected void pauseStreamingEvent() throws Exception {
		logger.info("sending single byte pause");
		this.comm.sendByteImmediately(GrblUtils.GRBL_PAUSE_COMMAND);
	}

	@Override
	protected void resumeStreamingEvent() throws Exception {
		logger.info("sending single byte resume");
		comm.sendByteImmediately(GrblUtils.GRBL_RESUME_COMMAND);

		// TODO: is this needed for real time mode?
		synchronized (this) {
			// need to resume otherwise the cmd will never go
			isResuming = true;
			comm.resumeSend();
			dispatchStateChange(ControlState.COMM_SENDING);
		}
	}

}
