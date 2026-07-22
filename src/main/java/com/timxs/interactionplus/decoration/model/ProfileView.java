package com.timxs.interactionplus.decoration.model;

import com.timxs.interactionplus.identity.model.PublicIdentityVo;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 当前佩戴视图。返回各槽位佩戴的装饰，并标记失效项。
 */
@Data
public class ProfileView {

    private String avatarFrame;
    private String title;
    private String primaryBadge;
    private List<String> badgeShowcase = new ArrayList<>();
    private String cardBackground;
    private String nameStyle;

    /** 当前佩戴中已失效的槽位明细（前台据此提示并清理）。 */
    private List<InvalidEquipItem> invalidItems = new ArrayList<>();

    /** 当前生效的身份标识（按角色映射，只读展示，不可佩戴/卸下）。 */
    private List<PublicIdentityVo.IdentityMarkVo> identityMarks = new ArrayList<>();

    /** 是否公开展示「我的装扮墙」；为空视为公开。 */
    private Boolean publicDecorationsVisible;
}
