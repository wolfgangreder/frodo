/*
 * Copyright 2026 Wolfgang Reder
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

package at.or.reder.frodo.mqtt;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
/**
 * MQTT messaging service for publishing and consuming messages.
 * Configure MQTT broker connection in application.properties.
 */
@ApplicationScoped
public class MqttService {

    private static final Logger LOG = Logger.getLogger(MqttService.class);

    @Inject
    @Channel("frodo_out")
    Emitter<String> emitter;

    /**
     * Publishes a message to the MQTT topic configured as "frodo_out".
     *
     * @param message the message payload to publish
     */
    public void publish(String message) {
        LOG.debugf("Publishing MQTT message: %s", message);
        emitter.send(message);
    }

    /**
     * Consumes messages from the MQTT topic configured as "frodo_in".
     *
     * @param message the received message payload
     */
    @Incoming("frodo_in")
    public void onMessage(String message) {
        LOG.infof("Received MQTT message: %s", message);
    }
}
