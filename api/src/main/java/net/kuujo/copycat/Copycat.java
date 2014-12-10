/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kuujo.copycat;

import net.kuujo.copycat.cluster.Cluster;
import net.kuujo.copycat.cluster.ClusterConfig;
import net.kuujo.copycat.collections.*;
import net.kuujo.copycat.election.LeaderElection;
import net.kuujo.copycat.internal.DefaultCopycat;
import net.kuujo.copycat.internal.util.Services;
import net.kuujo.copycat.spi.ExecutionContext;
import net.kuujo.copycat.spi.Protocol;

/**
 * Copycat.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public interface Copycat extends Managed {

  /**
   * Creates a new Copycat instance, loading the configuration from the classpath.
   *
   * @return The Copycat instance.
   */
  static Copycat create() {
    return copycat(Services.load("copycat.cluster"), Services.load("copycat.protocol"));
  }

  /**
   * Creates a new Copycat instance.
   *
   * @param cluster The global cluster configuration.
   * @param protocol The cluster protocol.
   * @return The Copycat instance.
   */
  static Copycat copycat(ClusterConfig cluster, Protocol protocol) {
    return new DefaultCopycat(cluster, protocol, ExecutionContext.create());
  }

  /**
   * Returns the Copycat cluster.
   *
   * @return The Copycat cluster.
   */
  Cluster cluster();

  /**
   * Creates a new event log.
   *
   * @param name The name of the event log to create.
   * @param <T> The event log entry type.
   * @return A completable future to be completed once the event log has been created.
   */
  <T> EventLog<T> eventLog(String name);

  /**
   * Creates a new event log.
   *
   * @param name The name of the event log to create.
   * @param cluster The initial event log cluster configuration.
   * @param <T> The event log entry type.
   * @return A completable future to be completed once the event log has been created.
   */
  <T> EventLog<T> eventLog(String name, ClusterConfig cluster);

  /**
   * Creates a new state log.
   *
   * @param name The name of the state log to create.
   * @param <T> The state log entry type.
   * @return A completable future to be completed once the state log has been created.
   */
  <T> StateLog<T> stateLog(String name);

  /**
   * Creates a new state log.
   *
   * @param name The name of the state log to create.
   * @param cluster The initial state log cluster configuration.
   * @param <T> The state log entry type.
   * @return A completable future to be completed once the state log has been created.
   */
  <T> StateLog<T> stateLog(String name, ClusterConfig cluster);

  /**
   * Creates a new replicated state machine.
   *
   * @param name The name of the state machine to create.
   * @param state The state machine's initial state.
   * @return A completable future to be completed once the state machine has been created.
   */
  <T extends State> StateMachine<T> stateMachine(String name, Class<T> stateType, T state);

  /**
   * Creates a new replicated state machine.
   *
   * @param name The name of the state machine to create.
   * @param state The state machine's initial state.
   * @param cluster The initial state machine cluster configuration.
   * @return A completable future to be completed once the state machine has been created.
   */
  <T extends State> StateMachine<T> stateMachine(String name, Class<T> stateType, T state, ClusterConfig cluster);

  /**
   * Creates a new leader election.
   *
   * @param name The leader election name.
   * @return A completable future to be completed once the leader election has been created.
   */
  LeaderElection election(String name);

  /**
   * Creates a new leader election.
   *
   * @param name The leader election name.
   * @param cluster The initial ELECTION cluster configuration.
   * @return A completable future to be completed once the leader election has been created.
   */
  LeaderElection election(String name, ClusterConfig cluster);

  /**
   * Returns a named asynchronous map.
   *
   * @param name The map name.
   * @param <K> The map key type.
   * @param <V> The map entry type.
   * @return An asynchronous map.
   */
  <K, V> AsyncMap<K, V> getMap(String name);

  /**
   * Returns a named asynchronous multimap.
   *
   * @param name The multimap name.
   * @param <K> The map key type.
   * @param <V> The map entry type.
   * @return An asynchronous multimap.
   */
  <K, V> AsyncMultiMap<K, V> getMultiMap(String name);

  /**
   * Returns a named asynchronous list.
   *
   * @param name The list name.
   * @param <T> The list entry type.
   * @return An asynchronous list.
   */
  <T> AsyncList<T> getList(String name);

  /**
   * Returns a named asynchronous set.
   *
   * @param name The set name.
   * @param <T> The set entry type.
   * @return An asynchronous set.
   */
  <T> AsyncSet<T> getSet(String name);

  /**
   * Returns a named asynchronous lock.
   *
   * @param name The lock name.
   * @return An asynchronous lock.
   */
  AsyncLock getLock(String name);

}