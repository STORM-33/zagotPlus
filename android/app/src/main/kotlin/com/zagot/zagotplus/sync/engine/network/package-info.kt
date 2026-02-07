/**
 * Network monitoring.
 *
 * This package provides network connectivity monitoring:
 * - [NetworkMonitor] - Interface for network state observation
 * - [AndroidNetworkMonitor] - Android implementation with debounce and validation ping
 * - [FakeNetworkMonitor] - Test fake for controlled network simulation
 *
 * The monitor uses a 3-second debounce to avoid flapping and performs
 * a validation ping to ensure actual internet access (captive portal detection).
 */
package com.zagot.zagotplus.sync.engine.network
