package org.torproject.jtor.circuits.impl;

import java.io.InputStream;
import java.io.OutputStream;

import org.torproject.jtor.TorException;
import org.torproject.jtor.circuits.Circuit;
import org.torproject.jtor.circuits.CircuitNode;
import org.torproject.jtor.circuits.Stream;
import org.torproject.jtor.circuits.cells.RelayCell;

public class StreamImpl implements Stream {
	private final CircuitImpl circuit;
	private final int streamId;
	private final CircuitNode targetNode;
	private final TorInputStream inputStream;
	private final TorOutputStream outputStream;
	private boolean isClosed;
	private boolean relayEndReceived;
	
	StreamImpl(CircuitImpl circuit, CircuitNode targetNode, int streamId) {
		this.circuit = circuit;
		this.targetNode = targetNode;
		this.streamId = streamId;
		this.inputStream = new TorInputStream(this);
		this.outputStream = new TorOutputStream(this);
	}

	void addInputCell(RelayCell cell) {
		if(isClosed)
			return;
		if(cell.getRelayCommand() == RelayCell.RELAY_END) {
			relayEndReceived = true;
			inputStream.addEndCell(cell);
		}
		else
			inputStream.addInputCell(cell);
	}

	public int getStreamId() {
		return streamId;
	}

	public Circuit getCircuit() {
		return circuit;
	}

	CircuitNode getTargetNode() {
		return targetNode;
	}

	public void close() {
		if(isClosed)
			return;
		isClosed = true;
		inputStream.close();
		outputStream.close();
		if(!relayEndReceived) {
			final RelayCell cell = new RelayCellImpl(circuit.getFinalCircuitNode(), circuit.getCircuitId(), streamId, RelayCell.RELAY_END);
			cell.putByte(RelayCell.REASON_DONE);
			circuit.sendRelayCellToFinalNode(cell);
		}
		// XXX when to remove?
		//circuit.removeStream(this);
	}

	void openDirectory() {
		final RelayCell cell = new RelayCellImpl(circuit.getFinalCircuitNode(), circuit.getCircuitId(), streamId, RelayCell.RELAY_BEGIN_DIR);
		circuit.sendRelayCellToFinalNode(cell);
		receiveRelayConnectedCell();
	}

	void openExit(String target, int port) {
		final RelayCell cell = new RelayCellImpl(circuit.getFinalCircuitNode(), circuit.getCircuitId(), streamId, RelayCell.RELAY_BEGIN);
		cell.putString(target + ":"+ port);
		circuit.sendRelayCellToFinalNode(cell);
		receiveRelayConnectedCell();
	}

	private RelayCell receiveRelayConnectedCell() {
		final RelayCell responseCell = circuit.receiveRelayCell();
		if(responseCell == null)
			throw new TorException("Timeout waiting for RELAY_CONNECTED cell.");

		final int command = responseCell.getRelayCommand();
		if(command != RelayCell.RELAY_CONNECTED)
			throw new TorException("Did not receive expected RELAY_CONNECTED cell.  cell = "+ responseCell);

		if(responseCell.getStreamId() != streamId) {
			circuit.removeStream(this);
			throw new TorException("Did not receive expected stream id");
		}
		return responseCell;
	}
	public InputStream getInputStream() {
		return inputStream;
	}

	public OutputStream getOutputStream() {
		return outputStream;
	}
	
	public String toString() {
		return "[Stream stream_id="+ streamId + " circuit="+ circuit +" ]";
	}
}
