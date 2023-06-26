/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2023 SonarSource SA
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarlint.eclipse.ui.internal.job;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.sonarsource.sonarlint.core.clientapi.client.progress.ProgressUpdateNotification;
import org.sonarsource.sonarlint.core.clientapi.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/**
 *  As the backend is now in charge of synchronization we want the user to see the progress even though it was not
 *  scheduled on the IDE side. Therefore we create fake jobs for every backend job which offers interaction
 *  possibilities for the user.
 */
public class BackendProgressJobScheduler {
  private static final BackendProgressJobScheduler INSTANCE = new BackendProgressJobScheduler();
  private ConcurrentHashMap<String, BackendProgressJob> jobPool = new ConcurrentHashMap<>();
  
  private BackendProgressJobScheduler() {
  }
  
  public static BackendProgressJobScheduler get() {
    return INSTANCE;
  }
  
  /** Start a new progress bar by using an IDE job */
  public CompletableFuture<Void> startProgress(StartProgressParams params) {
    var taskId = params.getTaskId();
    if (jobPool.containsKey(taskId)) {
      var errorMessage = "Job with ID " + taskId + " is already active, skip reporting it";
      SonarLintLogger.get().debug(errorMessage);
      return CompletableFuture.failedFuture(new IllegalArgumentException(errorMessage));
    }
    
    var jobStartFuture = new CompletableFuture<Void>();
    var job = new BackendProgressJob(params, jobStartFuture);
    jobPool.put(taskId, job);
    job.schedule();
    
    return jobStartFuture;
  }
  
  /** Update the progress bar IDE job */
  public void update(String taskId, ProgressUpdateNotification notification) {
    if (!jobPool.containsKey(taskId)) {
      SonarLintLogger.get().debug("Job with ID " + taskId + " is unknown, skip reporting it");
      return;
    }
    jobPool.get(taskId).update(notification);
  }
  
  /** Complete the progress bar IDE job */
  public void complete(String taskId) {
    if (!jobPool.containsKey(taskId)) {
      SonarLintLogger.get().debug("Job with ID " + taskId + " is unknown, skip reporting it");
      return;
    }
    jobPool.get(taskId).complete();
  }
  
  /** This job is only an IDE frontend for a job running in the SonarLintBackend */
  private static class BackendProgressJob extends Job {
    private Object waitMonitor = new Object();
    private CompletableFuture<Void> jobStartFuture;
    private AtomicReference<String> message;
    private AtomicInteger percentage = new AtomicInteger(0);
    private AtomicBoolean complete = new AtomicBoolean(false);
    
    public BackendProgressJob(StartProgressParams params, CompletableFuture<Void> jobStartFuture) {
      super(params.getTitle());
      setPriority(DECORATE);
      
      this.jobStartFuture = jobStartFuture;
      this.message = new AtomicReference<>(params.getMessage());
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
      monitor.setTaskName(message.get());
      monitor.worked(percentage.get());
      jobStartFuture.complete(null);
      
      while (!complete.get()) {
        synchronized(waitMonitor) {
          monitor.setTaskName(message.get());
          monitor.worked(percentage.get());
        }
      }
      
      monitor.done();
      return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
    }
    
    public void update(ProgressUpdateNotification notification) {
      var newMessage = notification.getMessage();
      if (newMessage != null) {
        message.set(newMessage);
      }
      percentage.set(notification.getPercentage());
      
      synchronized(waitMonitor) {
        waitMonitor.notify();
      }
    }
    
    public void complete() {
      complete.set(true);
      synchronized(waitMonitor) {
        waitMonitor.notify();
      }
    }
  }
}
