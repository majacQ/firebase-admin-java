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

package com.google.firebase.database.core;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.InternalHelpers;
import io.netty.util.concurrent.FastThreadLocal;

import java.util.HashMap;
import java.util.Map;

public class RepoManager {

  private static final RepoManager instance;

  static {
    instance = new RepoManager();
  }

  private final Map<Context, Map<String, Repo>> repos = new HashMap<>();

  public RepoManager() {}

  /**
   * Used for legacy unit tests. The public API should go through FirebaseDatabase which calls
   * createRepo.
   */
  public static Repo getRepo(Context ctx, RepoInfo info) {
    return instance.getLocalRepo(ctx, info);
  }

  public static Repo createRepo(Context ctx, RepoInfo info, FirebaseDatabase database) {
    return instance.createLocalRepo(ctx, info, database);
  }

  public static void destroy(Context ctx) {
    try {
      instance.destroyInternal(ctx);
    } finally {
      ctx.stop();
      // Clean up Netty thread locals, which will also clean up any dangling threadDeathWatcher
      // daemons. See https://github.com/netty/netty/issues/7310 for more context.
      FastThreadLocal.removeAll();
    }
  }

  public static void interrupt(Context ctx) {
    instance.interruptInternal(ctx);
  }

  public static void interrupt(final Repo repo) {
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.interrupt();
          }
        });
  }

  public static void resume(final Repo repo) {
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.resume();
          }
        });
  }

  public static void resume(Context ctx) {
    instance.resumeInternal(ctx);
  }

  private Repo getLocalRepo(Context ctx, RepoInfo info) {
    ctx.freeze(); // No-op if it's already frozen
    String repoHash = "https://" + info.host + "/" + info.namespace;
    synchronized (repos) {
      if (!repos.containsKey(ctx) || !repos.get(ctx).containsKey(repoHash)) {
        // Calling this should create the repo.
        InternalHelpers.createDatabaseForTests(
            FirebaseApp.getInstance(), info, (DatabaseConfig) ctx);
      }
      return repos.get(ctx).get(repoHash);
    }
  }

  private Repo createLocalRepo(Context ctx, RepoInfo info, FirebaseDatabase database) {
    ctx.freeze(); // No-op if it's already frozen
    String repoHash = "https://" + info.host + "/" + info.namespace;
    synchronized (repos) {
      if (!repos.containsKey(ctx)) {
        Map<String, Repo> innerMap = new HashMap<>();
        repos.put(ctx, innerMap);
      }
      Map<String, Repo> innerMap = repos.get(ctx);
      if (!innerMap.containsKey(repoHash)) {
        Repo repo = new Repo(info, ctx, database);
        innerMap.put(repoHash, repo);
        return repo;
      } else {
        throw new IllegalStateException("createLocalRepo() called for existing repo.");
      }
    }
  }

  private void interruptInternal(final Context ctx) {
    RunLoop runLoop = ctx.getRunLoop();
    if (runLoop != null) {
      runLoop.scheduleNow(
          new Runnable() {
            @Override
            public void run() {
              synchronized (repos) {
                boolean allEmpty = true;
                if (repos.containsKey(ctx)) {
                  for (Repo repo : repos.get(ctx).values()) {
                    repo.interrupt();
                    allEmpty = allEmpty && !repo.hasListeners();
                  }
                  if (allEmpty) {
                    ctx.stop();
                  }
                }
              }
            }
          });
    }
  }

  private void destroyInternal(final Context ctx) {
    RunLoop runLoop = ctx.getRunLoop();
    if (runLoop != null) {
      // RunLoop gets initialized before any Repo is created. Therefore we can assume that when
      // the RunLoop is not present, there's nothing to clean up.
      runLoop.scheduleNow(new Runnable() {
        @Override
        public void run() {
          synchronized (repos) {
            if (repos.containsKey(ctx)) {
              for (Repo repo : repos.get(ctx).values()) {
                repo.interrupt();
              }
            }
          }
        }
      });
    }
  }


  private void resumeInternal(final Context ctx) {
    RunLoop runLoop = ctx.getRunLoop();
    if (runLoop != null) {
      runLoop.scheduleNow(
          new Runnable() {
            @Override
            public void run() {
              synchronized (repos) {
                if (repos.containsKey(ctx)) {
                  for (Repo repo : repos.get(ctx).values()) {
                    repo.resume();
                  }
                }
              }
            }
          });
    }
  }
}
