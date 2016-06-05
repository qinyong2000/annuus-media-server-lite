package com.ams.io.network;

abstract class NetworkHandler implements Runnable {
    protected boolean running = true;
    protected Thread thread = null;

    public NetworkHandler(String name) {
        super();
        this.thread = new Thread(this, name);
    }

	public boolean isRunning() {
        return running;
    }

    public void start() {
        running = true;
        this.thread.start();
    }

    public void stop() {
        running = false;
        try {
            thread.interrupt();
            thread.join(5000);
        } catch (InterruptedException e) {
        }
    }
}
