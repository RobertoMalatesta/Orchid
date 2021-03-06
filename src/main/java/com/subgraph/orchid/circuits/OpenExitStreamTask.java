package com.subgraph.orchid.circuits;

import java.util.concurrent.TimeoutException;

import com.subgraph.orchid.ExitCircuit;
import com.subgraph.orchid.Stream;
import com.subgraph.orchid.StreamConnectFailedException;
import com.subgraph.orchid.logging.Logger;

public class OpenExitStreamTask implements Runnable {
    private static final Logger logger = Logger.getInstance(OpenExitStreamTask.class);
    private final ExitCircuit circuit;
    private final StreamExitRequest exitRequest;

    OpenExitStreamTask(ExitCircuit circuit, StreamExitRequest exitRequest) {
        this.circuit = circuit;
        this.exitRequest = exitRequest;
    }

    @Override
    public void run() {
        logger.debug("Attempting to open stream to "+ exitRequest);
        try {
            exitRequest.setCompletedSuccessfully(tryOpenExitStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exitRequest.setInterrupted();
        } catch (TimeoutException e) {
            circuit.markForClose();
            exitRequest.setCompletedTimeout();
        } catch (StreamConnectFailedException e) {
            if(!e.isReasonRetryable()) {
                exitRequest.setExitFailed();
                circuit.recordFailedExitTarget(exitRequest);
            } else {
                circuit.markForClose();
                exitRequest.setStreamOpenFailure(e.getReason());
            }
        }
    }

    private Stream tryOpenExitStream() throws InterruptedException, TimeoutException, StreamConnectFailedException {
        if(exitRequest.isAddressTarget()) {
            return circuit.openExitStream(exitRequest.getAddress(), exitRequest.getPort(), exitRequest.getStreamTimeout());
        } else {
            return circuit.openExitStream(exitRequest.getHostname(), exitRequest.getPort(), exitRequest.getStreamTimeout());
        }
    }
}