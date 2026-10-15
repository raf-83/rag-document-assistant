//package com.aidocumentvault.backend.processing.chunking.dev;
//
//import com.aidocumentvault.backend.processing.chunking.service.DocumentChunkingService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.context.annotation.Profile;
//import org.springframework.stereotype.Component;
//
//import java.util.UUID;
//
//@Component
//@Profile("local")
//@RequiredArgsConstructor
//public class ChunkingDevRunner implements CommandLineRunner {
//
//    private final DocumentChunkingService documentChunkingService;
//
//    @Override
//    public void run(String... args) {
//        documentChunkingService.chunkDocumentContent(
//                UUID.fromString("f011c1a2-dbb6-4521-88b1-a3c11b8966ea"),
//                UUID.fromString("39eaa736-df50-45cd-8f85-f58842f540a0"),
//                UUID.fromString("49c69eaf-1715-46c7-aeaf-a36bb54329f0")
//        );
//    }
//}
