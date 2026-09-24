package com.netpulse.backend.service;

import com.netpulse.backend.dao.EndpointDao;
import com.netpulse.backend.dto.EndpointRequest;
import com.netpulse.backend.exception.NotFoundException;
import com.netpulse.backend.exception.ValidationException;
import com.netpulse.backend.model.Endpoint;

import java.util.List;
import java.util.regex.Pattern;

/** Business rules for managing monitored targets. Controllers stay thin because of this class. */
public class EndpointService {

    private static final int MIN_INTERVAL_SEC = 1;
    private static final int MAX_INTERVAL_SEC = 3600;
    private static final int MIN_TIMEOUT_MS = 100;
    private static final int MAX_TIMEOUT_MS = 30_000;

    /** host[:port] - a hostname/IPv4 literal with an optional port. */
    private static final Pattern HOST_PORT =
            Pattern.compile("^[A-Za-z0-9._-]+(:\\d{1,5})?$");

    private final EndpointDao dao;

    public EndpointService(EndpointDao dao) {
        this.dao = dao;
    }

    public List<Endpoint> listAll() {
        return dao.findAll();
    }

    public Endpoint getById(long id) {
        return dao.findById(id)
                .orElseThrow(() -> new NotFoundException("No endpoint exists with id " + id));
    }

    public Endpoint create(EndpointRequest request) {
        if (request == null) {
            throw new ValidationException("Request body is required.");
        }

        String name = trimmed(request.getName());
        if (name.isEmpty()) {
            throw new ValidationException("Field 'name' must not be empty.");
        }
        if (name.length() > 120) {
            throw new ValidationException("Field 'name' must be 120 characters or fewer.");
        }

        String target = trimmed(request.getTargetAddress());
        if (target.isEmpty()) {
            throw new ValidationException("Field 'targetAddress' must not be empty.");
        }
        if (target.length() > 255) {
            throw new ValidationException("Field 'targetAddress' must be 255 characters or fewer.");
        }
        validateTarget(target);

        int interval = request.getCheckIntervalSec() == null ? 5 : request.getCheckIntervalSec();
        if (interval < MIN_INTERVAL_SEC || interval > MAX_INTERVAL_SEC) {
            throw new ValidationException(
                    "Field 'checkIntervalSec' must be between " + MIN_INTERVAL_SEC + " and " + MAX_INTERVAL_SEC + ".");
        }

        int timeout = request.getTimeoutMs() == null ? 1500 : request.getTimeoutMs();
        if (timeout < MIN_TIMEOUT_MS || timeout > MAX_TIMEOUT_MS) {
            throw new ValidationException(
                    "Field 'timeoutMs' must be between " + MIN_TIMEOUT_MS + " and " + MAX_TIMEOUT_MS + ".");
        }
        // A timeout longer than the interval guarantees task pile-up on the client.
        if (timeout > interval * 1000L) {
            throw new ValidationException(
                    "Field 'timeoutMs' must not exceed the check interval (" + (interval * 1000) + " ms).");
        }

        if (dao.existsByName(name)) {
            throw new ValidationException("An endpoint named '" + name + "' already exists.");
        }

        Endpoint endpoint = new Endpoint();
        endpoint.setName(name);
        endpoint.setTargetAddress(target);
        endpoint.setCheckIntervalSec(interval);
        endpoint.setTimeoutMs(timeout);
        endpoint.setActive(request.getActive() == null || request.getActive());
        return dao.insert(endpoint);
    }

    public void delete(long id) {
        if (!dao.deleteById(id)) {
            throw new NotFoundException("No endpoint exists with id " + id);
        }
    }

    /**
     * Accepts either an http/https URL or a bare {@code host[:port]} pair -
     * exactly the two shapes the client's probe engine knows how to handle.
     */
    private void validateTarget(String target) {
        String lower = target.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            try {
                java.net.URI uri = java.net.URI.create(target);
                if (uri.getHost() == null || uri.getHost().isBlank()) {
                    throw new ValidationException("Field 'targetAddress' is not a valid URL.");
                }
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("Field 'targetAddress' is not a valid URL.");
            }
            return;
        }
        if (!HOST_PORT.matcher(target).matches()) {
            throw new ValidationException(
                    "Field 'targetAddress' must be an http(s) URL or a host[:port] pair, e.g. 8.8.8.8:53.");
        }
        int colon = target.indexOf(':');
        if (colon >= 0) {
            int port = Integer.parseInt(target.substring(colon + 1));
            if (port < 1 || port > 65535) {
                throw new ValidationException("Port must be between 1 and 65535.");
            }
        }
    }

    private String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
