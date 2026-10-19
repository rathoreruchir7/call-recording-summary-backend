package dev.callsummary;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/call")
public class CallController {
    private final CallService service;
    public CallController(CallService service) { this.service = service; }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<CallRecord> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(file));
    }

    @GetMapping
    public Map<String, List<CallRecord>> list() {
        return Map.of("calls", service.list());
    }

    @GetMapping("/{id}/summary")
    public SummaryRecord getSummary(@PathVariable UUID id) { return service.getSummary(id); }

    @GetMapping("/{id}")
    public CallRecord get(@PathVariable UUID id) { return service.get(id); }
}
