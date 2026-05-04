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
