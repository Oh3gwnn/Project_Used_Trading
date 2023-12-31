package com.be05.market.service;

import com.be05.market.dto.ProposalDto;
import com.be05.market.dto.ResponseDto;
import com.be05.market.dto.mapping.ProposalPageInfoDto;
import com.be05.market.entity.ItemEntity;
import com.be05.market.entity.ProposalEntity;
import com.be05.market.entity.Role;
import com.be05.market.entity.UserEntity;
import com.be05.market.repository.ItemRepository;
import com.be05.market.repository.ProposalRepository;
import com.be05.market.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProposalService {
    private final ResponseDto responseDto = new ResponseDto();
    private final ProposalRepository proposalRepository;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final ItemService itemService;

    // Post Purchase Offer
    public void postOffer(Long itemId, ProposalDto proposalDto, Authentication authentication) {
        ItemEntity itemEntity = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        UserEntity userEntity = getUserEntity(authentication);
        if (proposalDto.getSuggestedPrice() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);

        proposalRepository.save(proposalDto.newEntity(itemEntity, userEntity));
    }

    // View Purchase Offer
    public Page<ProposalPageInfoDto> findPagedOffer(
            Long itemId, Authentication authentication, Integer page) {
        existById(itemId);
        ItemEntity itemEntity = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        UserEntity userEntity = getUserEntity(authentication);

        // 물품 등록 작성자, 비밀번호 == requestParam 작성자, 비밀번호
        // -> 맞으면 모든 구매 제안을 볼 수 있다.
        Pageable pageable = PageRequest.of(page-1, 25, Sort.by("id"));
        Page<ProposalEntity> proposalEntities;

        if ((itemEntity.getUser().getUserId().equals(userEntity.getUserId())
                && itemEntity.getUser().getPassword().equals(userEntity.getPassword()))
                || userEntity.getRole() == Role.ROLE_ADMIN) {
            proposalEntities = proposalRepository.findAll(pageable);
        }
        // 물품 등록 작성자, 비밀번호 != requestParam 작성자, 비밀번호
        // -> requestParam 작성자의 구매 제안만 볼 수 있다.
        else {
            proposalEntities = proposalRepository
                    .findAllByItemIdAndUser_userId(itemId, userEntity.getUserId(), pageable);
        }
        return proposalEntities.map(ProposalPageInfoDto::fromEntity);
    }

    // PUT Status or SuggestedPrice?
    public ResponseDto putUpdateOffer(Long proposalId, Long itemId,
                                      ProposalDto proposalDto,
                                      Authentication authentication) {
        ProposalEntity proposalEntity = validateProposalByItemId(proposalId, itemId);
        UserEntity userEntity = getUserEntity(authentication);
        ItemEntity itemEntity = itemService.getItemById(itemId);

        // 구매 제안 작성자일 때
        if(userEntity.getUserId().equals(proposalEntity.getUser().getUserId())) {
            if (userEntity.getRole() == Role.ROLE_USER)
                proposalEntity.validatePassword(userEntity.getPassword()); // 비밀번호 체크
            // 1) 구매 가격 제안 수정("제안" & SuggestedPrice != null)
            if (proposalEntity.getStatus().equals("제안")
                    && proposalDto.getSuggestedPrice() != null) {
                modifiedOffer(proposalEntity, proposalDto);
                responseDto.setMessage("제안이 수정되었습니다.");
                return responseDto;
            }
            // 2) 현재 "수락" 상태 & Request "확정" 상태 -> 판매 완료
            if (proposalEntity.getStatus().equals("수락")
                    && proposalDto.getStatus().equals("확정")) {
                itemEntity.setStatus("판매 완료");
                itemRepository.save(itemEntity);

                List<ProposalEntity> proposals = proposalRepository.findAll();
                for (ProposalEntity proposal : proposals) {
                    proposal.setStatus("거절");
                    proposalRepository.save(proposal);
                }
                proposalEntity.setStatus("확정");
                proposalRepository.save(proposalEntity);
                responseDto.setMessage("구매가 확정되었습니다.");

                return responseDto;
            }
        }
        // 물품 등록 작성자일 때
        if (userEntity.getUserId().equals(itemEntity.getUser().getUserId())
                || userEntity.getRole() == Role.ROLE_ADMIN) {
            if (userEntity.getRole() == Role.ROLE_USER)
                itemEntity.validatePassword(userEntity.getPassword());
            acceptRejectOffer(proposalEntity, itemEntity, proposalDto, authentication);
            return responseDto;
        } else throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    // Modifying Purchase Offer
    public void modifiedOffer(ProposalEntity proposalEntity,
                              ProposalDto proposalDto) {
        proposalEntity.setSuggestedPrice(proposalDto.getSuggestedPrice());
        proposalRepository.save(proposalEntity);
    }

    // Delete Purchase Offer
    public void deleteOffer(Long proposalId, Long itemId, Authentication authentication) {
        ProposalEntity proposalEntity = validateProposalByItemId(proposalId, itemId);
        UserEntity userEntity = getUserEntity(authentication);
        if (userEntity.getRole() == Role.ROLE_USER) {
            proposalEntity.validatePassword(userEntity.getPassword());
            // 구매 확정인데 지우려고 할 경우(ROLE_ADMIN 제외)
            if (proposalEntity.getStatus().equals("확정"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        proposalRepository.deleteById(proposalId);
    }

    // Accept, Reject Purchase Offer
    public void acceptRejectOffer(ProposalEntity proposalEntity, ItemEntity itemEntity,
                                  ProposalDto proposalDto, Authentication authentication) {
        UserEntity userEntity = getUserEntity(authentication);
        if (userEntity.getRole() == Role.ROLE_USER)
            itemEntity.validatePassword(userEntity.getPassword());
        proposalEntity.setStatus(proposalDto.getStatus());
        proposalRepository.save(proposalEntity);
        responseDto.setMessage("제안의 상태가 변경되었습니다.");
    }

    // Find User
    private UserEntity getUserEntity(Authentication authentication) {
        return userRepository.findByUserId(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // 해당 게시글이 존재하는지
    public void existById(Long itemId) {
        if (!itemRepository.existsById(itemId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    // 요청 구매 제안 유무, 대상 구매 제안이 대상 게시글의 제안인지 확인
    public ProposalEntity validateProposalByItemId(Long proposalId, Long itemId) {
        ItemEntity itemEntity = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        ProposalEntity proposalEntity = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!itemId.equals(itemEntity.getId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);

        return proposalEntity;
    }
}
