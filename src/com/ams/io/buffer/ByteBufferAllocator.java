package com.ams.io.buffer;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ByteBufferAllocator implements IByteBufferAllocator {
	final private Logger logger = LoggerFactory
	        .getLogger(ByteBufferAllocator.class);

	private ByteBuffer byteBuffer = null;

	private int chunkSize = 4 * 1024 * 1024; // 4M

	private int poolSize = 128 * 1024 * 1024; // 128M

	private ConcurrentLinkedQueue<ByteBuffer> chunkPool = new ConcurrentLinkedQueue<ByteBuffer>();

	public void init() {
		byteBuffer = null;
		for (int i = 0; i < poolSize / chunkSize; i++) {
			ByteBuffer buf = allocateBuffer(chunkSize);
			chunkPool.offer(buf);
		}
		ByteBufferCollector collector = new ByteBufferCollector();
		collector.start();
	}

	private ByteBuffer allocateBuffer(int size) {
		return ByteBuffer.allocateDirect(size);
	}

	private ByteBuffer newBuffer() {
		ByteBuffer chunk = chunkPool.poll();
		if (chunk == null) {
			chunk = allocateBuffer(chunkSize);
			logger.debug("allocate chunk from direct buffer");
		}
		ByteBuffer chunkHolder = chunk.duplicate();
		referenceList.add(new ChunkReference(chunkHolder, chunk));
		return chunkHolder;
	}

	private static ReferenceQueue<ByteBuffer> chunkReferenceQueue = new ReferenceQueue<ByteBuffer>();

	private class ChunkReference extends WeakReference<ByteBuffer> {
		private ByteBuffer chunk;

		public ChunkReference(ByteBuffer referent, ByteBuffer chunk) {
			super(referent, chunkReferenceQueue);
			this.chunk = chunk;
		}

		public ByteBuffer getChunk() {
			return chunk;
		}
	};

	private static List<ChunkReference> referenceList = Collections
	        .synchronizedList(new LinkedList<ChunkReference>());

	private class ByteBufferCollector extends Thread {
		private static final int COLLECT_INTERVAL_MS = 100;

		private void recycle(ByteBuffer buf) {
			chunkPool.offer(buf);
		}

		private void collect() {
			ChunkReference ref;
			int n = 0;
			while ((ref = (ChunkReference) chunkReferenceQueue.poll()) != null) {
				ByteBuffer chunk = ref.getChunk();
				recycle(chunk);
				referenceList.remove(ref);
				n++;
			}
			if (n > 0) {
				logger.debug("collected {} chunk buffers, chunk pool size: {}", n, chunkPool.size());
			}

		}

		public ByteBufferCollector() {
			super("ByteBufferCollector");
			try {
				setDaemon(true);
			} catch (Exception e) {
			}
		}

		public void run() {
			logger.debug("chunk pool size: {}", chunkPool.size());
			try {
				while (!Thread.interrupted()) {
					sleep(COLLECT_INTERVAL_MS);
					collect();
				}
			} catch (InterruptedException e) {
				interrupt();
			}
		}
	}

	public synchronized ByteBuffer allocate(int size) {
		if (byteBuffer == null
		        || byteBuffer.capacity() - byteBuffer.limit() < size) {
			byteBuffer = newBuffer();
		}
		byteBuffer.limit(byteBuffer.position() + size);
		ByteBuffer slice = byteBuffer.slice();
		byteBuffer.position(byteBuffer.limit());

		return slice;
	}

	public void setPoolSize(int poolSize) {
		this.poolSize = poolSize;
	}

	public void setChunkSize(int chunkSize) {
		this.chunkSize = chunkSize;
	}

}
