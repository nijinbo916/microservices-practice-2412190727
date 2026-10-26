package com.zjgsu.njb.pet.web;

import com.zjgsu.njb.pet.dto.CreatePetRequest;
import com.zjgsu.njb.pet.dto.PetResponse;
import com.zjgsu.njb.pet.service.PetService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 宠物档案 REST 接口。
 *
 * <pre>
 * POST /api/pets        建档            -&gt; 201 Created
 * GET  /api/pets        列表（可过滤）  -&gt; 200 OK
 * GET  /api/pets/{id}   详情            -&gt; 200 OK / 404 Not Found
 * </pre>
 */
@RestController
@RequestMapping("/api/pets")
public class PetController {

    private final PetService petService;

    public PetController(PetService petService) {
        this.petService = petService;
    }

    /** 为宠物建档，成功返回 201 与新建资源。 */
    @PostMapping
    public ResponseEntity<PetResponse> create(@Valid @RequestBody CreatePetRequest request) {
        PetResponse created = petService.createPet(request);
        return ResponseEntity.created(URI.create("/api/pets/" + created.id())).body(created);
    }

    /** 查询宠物列表，支持按主人姓名或物种过滤。 */
    @GetMapping
    public List<PetResponse> list(@RequestParam(required = false) String ownerName,
                                  @RequestParam(required = false) String species) {
        return petService.listPets(ownerName, species);
    }

    /** 查询单只宠物档案。 */
    @GetMapping("/{id}")
    public PetResponse get(@PathVariable Long id) {
        return petService.getPet(id);
    }
}
