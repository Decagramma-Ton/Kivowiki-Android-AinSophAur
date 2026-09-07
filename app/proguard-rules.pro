# Retrofit、Room、Coil、Hilt 通过各自 consumer rules 保留必要类型。
# 不使用全包 -keep，以实际 Release 安装验证 R8 结果。
-keepattributes Signature,InnerClasses,EnclosingMethod
