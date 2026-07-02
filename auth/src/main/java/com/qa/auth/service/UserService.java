package com.qa.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qa.auth.constant.AuthConstants;
import com.qa.auth.dto.LoginResult;
import com.qa.auth.dto.request.ChangePasswordRequest;
import com.qa.auth.dto.request.CreateUserRequest;
import com.qa.auth.dto.request.UpdateProfileRequest;
import com.qa.auth.dto.request.UpdateUserRequest;
import com.qa.auth.dto.response.UserVO;
import com.qa.auth.entity.SysRoleEntity;
import com.qa.auth.entity.SysUserEntity;
import com.qa.auth.entity.SysUserRoleEntity;
import com.qa.auth.mapper.SysRoleMapper;
import com.qa.auth.mapper.SysUserMapper;
import com.qa.auth.mapper.SysUserRoleMapper;
import com.myenglish.qacommon.context.UserContextHolder;
import com.myenglish.qacommon.dto.ApiCode;
import com.myenglish.qacommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final AuthQueryService authQueryService;
    private final RefreshTokenService refreshTokenService;
    private final LoginLockService loginLockService;
    private final EmailService emailService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ======================== 注册 ========================

    @Transactional
    public boolean register(String username, String password, String email) {
        if (findByUsername(username).isPresent()) {
            log.info("注册跳过，用户名已存在: {}", username);
            return false;
        }
        if (email != null && findByEmail(email).isPresent()) {
            throw new BusinessException(ApiCode.EMAIL_ALREADY_EXISTS, "邮箱已被注册");
        }
        SysUserEntity user = new SysUserEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(username);
        if (email != null) {
            user.setEmail(email.trim().toLowerCase());
        }
        user.setEnabled(true);
        user.setDeleted(false);
        sysUserMapper.insert(user);
        bindRoles(user.getId(), List.of(AuthConstants.ROLE_USER), null);
        log.info("用户注册成功: username={}, email={}", username, email);
        return true;
    }

    @Transactional
    public String registerByEmail(String email) {
        if (findByEmail(email).isPresent()) {
            throw new BusinessException(ApiCode.EMAIL_ALREADY_EXISTS, "邮箱已被注册");
        }
        String username = generateUniqueUsername(email);
        String defaultPassword = generateDefaultPassword();

        SysUserEntity user = new SysUserEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(defaultPassword));
        user.setNickname(username);
        user.setEmail(email);
        user.setEnabled(true);
        user.setDeleted(false);
        sysUserMapper.insert(user);
        bindRoles(user.getId(), List.of(AuthConstants.ROLE_USER), null);

        emailService.sendDefaultPassword(email, username, defaultPassword);
        log.info("邮箱注册成功: username={}, email={}", username, email);
        return username;
    }

    private String generateUniqueUsername(String email) {
        String prefix = email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9_]", "_");
        if (prefix.length() < 3) prefix = "user_" + prefix;
        String candidate = prefix;
        int suffix = 1;
        while (findByUsername(candidate).isPresent()) {
            candidate = prefix + suffix++;
        }
        return candidate;
    }

    private String generateDefaultPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnpqrstuvwxyz";
        String digits = "23456789";
        String special = "@#$%&*";
        String all = upper + lower + digits + special;
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(lower.charAt(random.nextInt(lower.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        sb.append(special.charAt(random.nextInt(special.length())));
        for (int i = 4; i < 12; i++) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp;
        }
        return new String(chars);
    }

    // ======================== 登录 ========================

    public LoginResult login(String account, String rawPassword, String clientIp, String loginType) {
        boolean isEmailLogin = "EMAIL".equalsIgnoreCase(loginType);
        Optional<SysUserEntity> userOpt = isEmailLogin
                ? findActiveByEmail(account) : findActiveByUsername(account);

        String lockTarget = userOpt.map(SysUserEntity::getUsername).orElse(account);
        if (loginLockService.isLocked(lockTarget)) {
            long sec = loginLockService.getLockRemainingSeconds(lockTarget);
            return LoginResult.accountLocked(sec);
        }

        if (userOpt.isEmpty()) {
            loginLockService.recordFail(lockTarget);
            return LoginResult.userNotFound();
        }

        SysUserEntity user = userOpt.get();
        if (Boolean.FALSE.equals(user.getEnabled())) {
            return LoginResult.userDisabled();
        }

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            loginLockService.recordFail(lockTarget);
            int remaining = loginLockService.getRemainingAttempts(lockTarget);
            log.warn("登录密码错误: account={}, remainingAttempts={}", account, remaining);
            if (remaining <= 0) {
                return LoginResult.accountLocked(loginLockService.getLockRemainingSeconds(lockTarget));
            }
            return LoginResult.passwordError();
        }

        loginLockService.clearFail(lockTarget);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(clientIp);
        sysUserMapper.updateById(user);
        log.info("用户登录成功: username={}", user.getUsername());
        return LoginResult.success(user);
    }

    // ======================== 查询 ========================

    public Optional<SysUserEntity> findByUsername(String username) {
        return Optional.ofNullable(sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUserEntity>().eq(SysUserEntity::getUsername, username)));
    }

    public Optional<SysUserEntity> findActiveByUsername(String username) {
        return Optional.ofNullable(sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUserEntity>()
                        .eq(SysUserEntity::getUsername, username)
                        .eq(SysUserEntity::getDeleted, false)));
    }

    public Optional<SysUserEntity> findByEmail(String email) {
        return Optional.ofNullable(sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUserEntity>().eq(SysUserEntity::getEmail, email)));
    }

    public Optional<SysUserEntity> findActiveByEmail(String email) {
        return Optional.ofNullable(sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUserEntity>()
                        .eq(SysUserEntity::getEmail, email)
                        .eq(SysUserEntity::getDeleted, false)));
    }

    public Optional<SysUserEntity> findById(Long id) {
        SysUserEntity user = sysUserMapper.selectById(id);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) return Optional.empty();
        return Optional.of(user);
    }

    // ======================== 用户管理 ========================

    public List<UserVO> listUsers() {
        List<SysUserEntity> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUserEntity>().eq(SysUserEntity::getDeleted, false));
        return users.stream().map(this::toUserVO).collect(Collectors.toList());
    }

    @Transactional
    public UserVO createUser(CreateUserRequest req) {
        if (findByUsername(req.getUsername()).isPresent()) {
            throw new BusinessException(ApiCode.USER_ALREADY_EXISTS, "用户已存在");
        }
        SysUserEntity user = new SysUserEntity();
        user.setUsername(req.getUsername().trim());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setRealName(req.getRealName());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setEnabled(true);
        user.setDeleted(false);
        sysUserMapper.insert(user);
        bindRoles(user.getId(), List.of(AuthConstants.ROLE_USER), UserContextHolder.getUserId());
        return toUserVO(user);
    }

    public UserVO getProfile(Long id) {
        SysUserEntity user = findById(id).orElseThrow(() ->
                new BusinessException(ApiCode.USER_NOT_FOUND, "用户不存在"));
        return toUserVO(user);
    }

    @Transactional
    public UserVO updateProfile(Long userId, UpdateProfileRequest req) {
        SysUserEntity user = findById(userId).orElseThrow(() ->
                new BusinessException(ApiCode.USER_NOT_FOUND, "用户不存在"));
        if (StringUtils.hasText(req.getNickname())) user.setNickname(req.getNickname().trim());
        if (StringUtils.hasText(req.getRealName())) user.setRealName(req.getRealName().trim());
        if (req.getEmail() != null) {
            validateUniqueEmail(userId, req.getEmail());
            user.setEmail(StringUtils.hasText(req.getEmail()) ? req.getEmail().trim() : null);
        }
        if (req.getPhone() != null) {
            validateUniquePhone(userId, req.getPhone());
            user.setPhone(StringUtils.hasText(req.getPhone()) ? req.getPhone().trim() : null);
        }
        if (req.getAvatar() != null) user.setAvatar(req.getAvatar());
        if (req.getGender() != null) user.setGender(req.getGender());
        user.setUpdatedBy(userId);
        sysUserMapper.updateById(user);
        log.info("用户资料更新: userId={}", userId);
        return toUserVO(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        SysUserEntity user = findById(userId).orElseThrow(() ->
                new BusinessException(ApiCode.USER_NOT_FOUND, "用户不存在"));
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ApiCode.INVALID_PASSWORD, "原密码错误");
        }
        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "新密码不能与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setUpdatedBy(userId);
        sysUserMapper.updateById(user);
        refreshTokenService.deleteByUserId(userId);
        log.info("用户修改密码: userId={}", userId);
    }

    public void logout(Long userId) {
        refreshTokenService.deleteByUserId(userId);
        log.info("用户登出: userId={}", userId);
    }

    @Transactional
    public UserVO updateUser(Long id, UpdateUserRequest req) {
        SysUserEntity user = findById(id).orElseThrow(() ->
                new BusinessException(ApiCode.USER_NOT_FOUND, "用户不存在"));
        if (StringUtils.hasText(req.getNickname())) user.setNickname(req.getNickname());
        if (StringUtils.hasText(req.getRealName())) user.setRealName(req.getRealName());
        if (req.getEmail() != null) {
            validateUniqueEmail(id, req.getEmail());
            user.setEmail(req.getEmail());
        }
        if (req.getPhone() != null) {
            validateUniquePhone(id, req.getPhone());
            user.setPhone(req.getPhone());
        }
        if (req.getAvatar() != null) user.setAvatar(req.getAvatar());
        if (req.getGender() != null) user.setGender(req.getGender());
        if (req.getEnabled() != null) user.setEnabled(req.getEnabled());
        if (StringUtils.hasText(req.getPassword())) user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setUpdatedBy(UserContextHolder.getUserId());
        sysUserMapper.updateById(user);
        return toUserVO(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        SysUserEntity user = findById(id).orElseThrow(() ->
                new BusinessException(ApiCode.USER_NOT_FOUND, "用户不存在"));
        user.setDeleted(true);
        user.setEnabled(false);
        sysUserMapper.updateById(user);
    }

    @Transactional
    public void assignRoles(Long userId, List<String> roleCodes) {
        SysUserEntity user = findById(userId).orElseThrow(() ->
                new BusinessException(ApiCode.USER_NOT_FOUND, "用户不存在"));
        validateRoleAssignment(roleCodes);
        bindRoles(userId, roleCodes, UserContextHolder.getUserId());
        // 权限变更：递增 tokenVersion 使旧 JWT 失效，并清除 refresh token 强制重新登录
        incrementTokenVersion(user);
        refreshTokenService.deleteByUserId(userId);
        log.info("用户角色分配变更，tokenVersion递增: userId={}", userId);
    }

    // ======================== 私有方法 ========================

    private void validateRoleAssignment(List<String> roleCodes) {
        if (roleCodes.contains(AuthConstants.ROLE_SUPER_ADMIN)
                && !UserContextHolder.hasRole(AuthConstants.ROLE_SUPER_ADMIN)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权分配超级管理员角色");
        }
    }

    private void bindRoles(Long userId, List<String> roleCodes, Long operatorId) {
        sysUserRoleMapper.deleteByUserId(userId);
        for (String roleCode : roleCodes) {
            SysRoleEntity role = sysRoleMapper.selectOne(
                    new LambdaQueryWrapper<SysRoleEntity>().eq(SysRoleEntity::getRoleCode, roleCode));
            if (role == null) throw new BusinessException(ApiCode.BAD_REQUEST, "角色不存在: " + roleCode);
            SysUserRoleEntity ur = new SysUserRoleEntity();
            ur.setUserId(userId);
            ur.setRoleId(role.getId());
            sysUserRoleMapper.insert(ur);
        }
    }

    private void validateUniqueEmail(Long userId, String email) {
        if (!StringUtils.hasText(email)) return;
        SysUserEntity existing = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUserEntity>()
                        .eq(SysUserEntity::getEmail, email.trim())
                        .ne(SysUserEntity::getId, userId)
                        .eq(SysUserEntity::getDeleted, false));
        if (existing != null) throw new BusinessException(ApiCode.DATA_ALREADY_EXISTS, "邮箱已被使用");
    }

    private void validateUniquePhone(Long userId, String phone) {
        if (!StringUtils.hasText(phone)) return;
        SysUserEntity existing = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUserEntity>()
                        .eq(SysUserEntity::getPhone, phone.trim())
                        .ne(SysUserEntity::getId, userId)
                        .eq(SysUserEntity::getDeleted, false));
        if (existing != null) throw new BusinessException(ApiCode.DATA_ALREADY_EXISTS, "手机号已被使用");
    }

    private UserVO toUserVO(SysUserEntity user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setGender(user.getGender());
        vo.setEnabled(user.getEnabled());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setRoles(authQueryService.loadAuthorities(user.getId()).getRoles());
        return vo;
    }

    /**
     * 递增用户的 Token 版本号，使旧的 JWT 因版本不匹配而失效
     */
    private void incrementTokenVersion(SysUserEntity user) {
        Long currentVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0L;
        user.setTokenVersion(currentVersion + 1);
        sysUserMapper.updateById(user);
    }
}
