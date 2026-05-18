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

import React from 'react';
import {
  Modal,
  ModalHeader,
  ModalBody,
  ModalFooter,
  Button,
  Label,
  Spinner,
  Divider,
  Flex,
  FlexItem,
} from '@patternfly/react-core';
import { InfoCircleIcon, SyncAltIcon } from '@patternfly/react-icons';
import { formatForDisplay } from '../../utils/timeZone';

const C = {
  primary: 'var(--pf-t--global--color--brand--default, #0066cc)',
  subtle:  'var(--pf-t--global--text-color--subtle, #6a6e73)',
};

/**
 * Info row component
 */
function InfoRow({ label, value }) {
  return (
    <div style={{ display: 'flex', padding: '0.25rem 0' }}>
      <span style={{ width: 140, flexShrink: 0, fontSize: '0.875rem', color: C.subtle }}>{label}</span>
      <span style={{ fontSize: '0.875rem' }}>
        {value || <em style={{ color: C.subtle }}>Not available</em>}
      </span>
    </div>
  );
}

/**
 * Device info dialog showing device identification details
 *
 * @param {Object} props
 * @param {boolean} props.open - Whether dialog is open
 * @param {Function} props.onClose - Callback when dialog closes
 * @param {Function} props.onRefresh - Callback to refresh device info
 * @param {Object} props.device - Device data
 * @param {Object} props.deviceInfo - Device identification info (FC 0x2B result)
 * @param {boolean} props.isLoading - Whether info is loading
 * @param {boolean} props.isRefreshing - Whether info is being refreshed
 */
function DeviceInfoDialog({
  open,
  onClose,
  onRefresh,
  device,
  deviceInfo,
  isLoading = false,
  isRefreshing = false,
}) {
  if (!device) return null;

  return (
    <Modal
      isOpen={open}
      onClose={onClose}
      variant="medium"
      aria-labelledby="device-info-title"
    >
      <ModalHeader
        title={
          <Flex gap={{ default: 'gapSm' }} alignItems={{ default: 'alignItemsCenter' }}>
            <FlexItem>
              <InfoCircleIcon style={{ color: 'var(--pf-t--global--color--status--info--default, #0066cc)' }} />
            </FlexItem>
            <FlexItem id="device-info-title">Device Information</FlexItem>
          </Flex>
        }
      />
      <ModalBody>
        {isLoading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: '2rem' }}>
            <Spinner />
          </div>
        ) : (
          <div>
            {/* Configuration */}
            <div style={{ fontSize: '0.875rem', fontWeight: 600, color: C.primary, marginBottom: '0.5rem' }}>
              Configuration
            </div>
            <InfoRow label="Name" value={device.name} />
            <InfoRow label="Host" value={`${device.host}:${device.port}`} />
            <InfoRow label="Unit ID" value={device.unitId} />
            <InfoRow
              label="Status"
              value={
                <Label color={device.enabled ? 'blue' : 'grey'}>
                  {device.enabled ? 'Enabled' : 'Disabled'}
                </Label>
              }
            />

            <Divider style={{ margin: '1rem 0' }} />

            {/* Device Identification */}
            <div style={{ fontSize: '0.875rem', fontWeight: 600, color: C.primary, marginBottom: '0.5rem' }}>
              Device Identification
            </div>

            {deviceInfo ? (
              <>
                <InfoRow label="Vendor" value={deviceInfo.vendorName} />
                <InfoRow label="Product Code" value={deviceInfo.productCode} />
                <InfoRow label="Revision" value={deviceInfo.revision} />
                <InfoRow label="Vendor URL" value={deviceInfo.vendorUrl} />
                <InfoRow label="Product Name" value={deviceInfo.productName} />
                <InfoRow label="Model Name" value={deviceInfo.modelName} />
                <InfoRow label="User App Name" value={deviceInfo.userApplicationName} />

                {deviceInfo.lastUpdated && (
                  <div style={{ marginTop: '1rem', fontSize: '0.75rem', color: C.subtle }}>
                    Last updated: {formatForDisplay(deviceInfo.lastUpdated)}
                  </div>
                )}
              </>
            ) : (
              <p style={{ fontSize: '0.875rem', color: C.subtle, padding: '0.5rem 0' }}>
                No device identification data available. Click "Refresh" to fetch from device.
              </p>
            )}
          </div>
        )}
      </ModalBody>
      <ModalFooter>
        <Button
          variant="secondary"
          onClick={() => onRefresh(device.id)}
          isDisabled={isRefreshing}
          icon={isRefreshing ? <Spinner size="sm" /> : <SyncAltIcon />}
        >
          {isRefreshing ? 'Refreshing...' : 'Refresh'}
        </Button>
        <Button variant="primary" onClick={onClose}>
          Close
        </Button>
      </ModalFooter>
    </Modal>
  );
}

export default DeviceInfoDialog;
