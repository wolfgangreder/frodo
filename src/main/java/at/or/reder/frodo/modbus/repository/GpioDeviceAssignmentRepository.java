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

package at.or.reder.frodo.modbus.repository;

import at.or.reder.frodo.modbus.entity.GpioDeviceAssignmentEntity;

import io.quarkus.hibernate.orm.panache.PanacheRepository;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

/**
 * Repository for GPIO pair ↔ device assignments.
 */
@ApplicationScoped
public class GpioDeviceAssignmentRepository
    implements PanacheRepository<GpioDeviceAssignmentEntity> {

  public Optional<GpioDeviceAssignmentEntity> findByDeviceId(Long deviceId) {
    return find("deviceId", deviceId).firstResultOptional();
  }

  public Optional<GpioDeviceAssignmentEntity> findByPairName(String pairName) {
    return find("gpioPairName", pairName).firstResultOptional();
  }

  public List<GpioDeviceAssignmentEntity> listAll() {
    return find("ORDER BY gpioPairName").list();
  }
}
