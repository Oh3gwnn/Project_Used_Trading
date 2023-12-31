package com.be05.market.controller;

import com.be05.market.dto.mapping.ContentInfoDto;
import com.be05.market.dto.mapping.ItemPageInfoDto;
import com.be05.market.dto.ResponseDto;
import com.be05.market.dto.SalesItemDto;
import com.be05.market.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
public class ItemController {
    private final ItemService itemService;
    private final ResponseDto responseDto = new ResponseDto();

    // TODO: POST /items
    @PostMapping
    public ResponseDto create(@RequestBody SalesItemDto items,
                              Authentication authentication) {
        itemService.createItem(items, authentication);
        responseDto.setMessage("등록이 완료되었습니다.");
        return responseDto;
    }

    // TODO: GET /items?page={page}&limit={limit}
    @GetMapping
    public Page<ItemPageInfoDto> readAll(
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit
    ) {
        return itemService.readItemsPaged(page, limit);
    }

    // TODO: GET /items/{itemId}
    @GetMapping("/{itemId}")
    public ContentInfoDto readOne(@PathVariable("itemId") Long id) {
        return itemService.read(id);
    }

    // TODO: PUT /items/{itemId}
    @PutMapping("/{itemId}")
    public ResponseDto update(@PathVariable Long itemId,
                             @RequestBody SalesItemDto items,
                              Authentication authentication) {
        itemService.updateItem(itemId, items, authentication);
        responseDto.setMessage("물품이 수정되었습니다.");
        return responseDto;
    }

    // TODO: PUT /items/{itemId}/image
    @PutMapping(
            value = "/{itemId}/image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDto uploadImage(@PathVariable Long itemId,
                                   @RequestParam("image") MultipartFile itemFile,
                                   Authentication authentication) {
        itemService.uploadItemImage(itemId, itemFile, authentication);
        responseDto.setMessage("이미지가 등록되었습니다.");
        return responseDto;
    }

    // TODO: DELETE /items/{itemId}
    @DeleteMapping("/{itemId}")
    public ResponseDto delete(@PathVariable Long itemId, Authentication authentication) {
        itemService.deleteItem(itemId, authentication);
        responseDto.setMessage("물품을 삭제했습니다.");
        return responseDto;
    }
}
