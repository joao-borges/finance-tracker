package ca.joaoborges.finance.transaction;

import ca.joaoborges.finance.common.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface TransactionMapper {

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "accountName", source = "account.name")
    @Mapping(target = "accountLogoUrl", source = "account.logoUrl")
    @Mapping(target = "accountOffBudget", source = "account.offBudget")
    @Mapping(target = "tags", expression =
            "java(transaction.getTags().stream().map(ca.joaoborges.finance.tag.Tag::getName).sorted().toList())")
    @Mapping(target = "dateAdjusted", expression =
            "java(transaction.getSourcePostedAt() != null && !transaction.getSourcePostedAt().equals(transaction.getPostedAt()))")
    @Mapping(target = "merchantId", source = "merchant.id")
    @Mapping(target = "merchant", source = "merchant.name")
    @Mapping(target = "merchantLogoUrl", source = "merchant.logoUrl")
    @Mapping(target = "merchantIcon", source = "merchant.icon")
    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "categoryIcon", source = "category.icon")
    @Mapping(target = "splitParentId", source = "splitParent.id")
    @Mapping(target = "matchedWithId", source = "matchedWith.id")
    TransactionDto toDto(Transaction transaction);

}
