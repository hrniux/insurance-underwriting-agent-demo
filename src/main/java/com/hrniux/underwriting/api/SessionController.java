package com.hrniux.underwriting.api;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hrniux.underwriting.session.SessionService;
import com.hrniux.underwriting.session.UnderwritingSession;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessions;

    public SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    public ResponseEntity<UnderwritingSession> create() {
        UnderwritingSession session = sessions.createSession();
        return ResponseEntity.created(URI.create("/api/v1/sessions/" + session.id())).body(session);
    }

    @GetMapping("/{sessionId}")
    public UnderwritingSession get(@PathVariable String sessionId) {
        return sessions.getSession(sessionId);
    }
}
