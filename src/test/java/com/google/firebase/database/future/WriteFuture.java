/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.database.future;

import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.TestFailure;
import com.google.firebase.testing.TestUtils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** User: greg Date: 5/24/13 Time: 2:37 PM */
public class WriteFuture implements Future<DatabaseError> {

  private DatabaseError error;
  private Semaphore semaphore;
  private boolean done = false;

  public WriteFuture(DatabaseReference ref, Object value) {
    semaphore = new Semaphore(0);
    ref.setValue(
        value,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            WriteFuture.this.error = error;
            finish();
          }
        });
  }

  public WriteFuture(DatabaseReference ref, Object value, double priority) {
    semaphore = new Semaphore(0);
    ref.setValue(
        value,
        priority,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            WriteFuture.this.error = error;
            finish();
          }
        });
  }

  public WriteFuture(DatabaseReference ref, Object value, String priority) {
    semaphore = new Semaphore(0);
    ref.setValue(
        value,
        priority,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            WriteFuture.this.error = error;
            finish();
          }
        });
  }

  private void finish() {
    done = true;
    semaphore.release(1);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return false;
  }

  @Override
  public boolean isCancelled() {
    return error != null;
  }

  @Override
  public boolean isDone() {
    return done;
  }

  @Override
  public DatabaseError get() throws InterruptedException, ExecutionException {
    semaphore.acquire(1);
    return error;
  }

  @Override
  public DatabaseError get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    boolean success = semaphore.tryAcquire(1, timeout, unit);
    if (!success) {
      throw new TimeoutException();
    }
    return error;
  }

  public DatabaseError timedGet()
      throws ExecutionException, TimeoutException, InterruptedException, TestFailure {
    if (error != null) {
      throw new TestFailure(error.getMessage());
    }
    return get(TestUtils.TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
  }
}
