package org.koitharu.kotatsu.core.logs

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ArrayBlockingQueue
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_ENTRIES = 10_000

@Singleton
class AppLogger @Inject constructor() {

	@Volatile
	var isEnabled: Boolean = false
		private set

	private val buffer = ArrayBlockingQueue<String>(MAX_ENTRIES)
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val stateLock = Any()
	private var readerJob: Job? = null
	private var logcatProcess: Process? = null
	private var generation = 0

	fun setEnabled(enabled: Boolean) {
		synchronized(stateLock) {
			if (enabled == isEnabled) return
			isEnabled = enabled
			generation++
			if (enabled) {
				buffer.clear()
				startReadingLocked(generation)
			} else {
				stopReadingLocked()
			}
		}
	}

	suspend fun stopAndDrainToString(): String {
		val job = synchronized(stateLock) {
			if (isEnabled) {
				isEnabled = false
				generation++
			}
			stopReadingLocked()
		}
		job?.join()
		return drainToString()
	}

	private fun drainToString(): String {
		val lines = ArrayList<String>(buffer.size)
		buffer.drainTo(lines)
		return lines.joinToString("\n")
	}

	private fun startReadingLocked(readerGeneration: Int) {
		val job = scope.launch(start = CoroutineStart.LAZY) {
			var process: Process? = null
			try {
				val pid = android.os.Process.myPid().toString()
				val startedProcess = Runtime.getRuntime().exec(arrayOf("logcat", "-v", "threadtime", "--pid", pid))
				process = startedProcess
				synchronized(stateLock) {
					if (!isEnabled || generation != readerGeneration) {
						startedProcess.destroy()
						return@launch
					}
					logcatProcess = startedProcess
				}
				BufferedReader(InputStreamReader(startedProcess.inputStream)).use { reader ->
					while (isActive) {
						val line = reader.readLine() ?: break
						if (!buffer.offer(line)) {
							buffer.poll()
							buffer.offer(line)
						}
					}
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e("AppLogger", "Failed to read logcat", e)
			} finally {
				process?.destroy()
				synchronized(stateLock) {
					if (generation == readerGeneration) {
						logcatProcess = null
						readerJob = null
					}
				}
			}
		}
		readerJob = job
		job.start()
	}

	private fun stopReadingLocked(): Job? {
		val job = readerJob
		readerJob = null
		job?.cancel()
		logcatProcess?.let { process ->
			runCatching { process.inputStream.close() }
			process.destroy()
		}
		logcatProcess = null
		return job
	}
}
