/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package io.atomix.group.messaging;

import io.atomix.resource.ResourceException;

/**
 * Exception thrown when a {@link Message} fails to be delivered or is explicitly {@link Message#fail() failed}
 * by a consumer.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
public class MessageFailedException extends ResourceException {

  public MessageFailedException() {
  }

  public MessageFailedException(String message) {
    super(message);
  }

  public MessageFailedException(String message, Throwable cause) {
    super(message, cause);
  }

  public MessageFailedException(Throwable cause) {
    super(cause);
  }

}
