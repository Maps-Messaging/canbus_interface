/*
 *   Copyright [ 2024 -  2026 ] MapsMessaging B.V.
 *
 *   Licensed under the Apache License, Version 2.0 with the Commons Clause
 *   (the "License"); you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at:
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *       https://commonsclause.com/
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package io.mapsmessaging.canbus.device;

import io.mapsmessaging.canbus.device.frames.CanFrame;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
final class CanWriteMessage {

  private final List<CanFrame> canFrames;
  private final int estimatedBits;

  CanWriteMessage(List<CanFrame> canFrames, int estimatedBits) {
    if (canFrames == null) {
      throw new IllegalArgumentException("canFrames must not be null");
    }
    if (canFrames.isEmpty()) {
      throw new IllegalArgumentException("canFrames must not be empty");
    }
    if (estimatedBits <= 0) {
      throw new IllegalArgumentException("estimatedBits must be > 0");
    }

    for (CanFrame canFrame : canFrames) {
      if (canFrame == null) {
        throw new IllegalArgumentException("canFrames must not contain null entries");
      }
    }

    this.canFrames = Collections.unmodifiableList(new ArrayList<>(canFrames));
    this.estimatedBits = estimatedBits;
  }
}