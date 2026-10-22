package ca.joaoborges.finance.amazon;

import ca.joaoborges.finance.common.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface AmazonOrderMapper {

    @Mapping(target = "url", expression =
            "java(\"https://\" + order.getMarketplace() + \"/gp/your-account/order-details?orderID=\" + order.getOrderNumber())")
    AmazonOrderDto toDto(AmazonOrder order);

    AmazonOrderDto.Item toDto(AmazonOrderItem item);

    @Mapping(target = "transactionId", source = "transaction.id")
    @Mapping(target = "transactionPostedAt", source = "transaction.postedAt")
    @Mapping(target = "transactionAccountName", source = "transaction.account.name")
    @Mapping(target = "transactionCategoryId", source = "transaction.category.id")
    AmazonOrderDto.Charge toDto(AmazonCharge charge);

}
