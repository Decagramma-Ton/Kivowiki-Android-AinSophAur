# 使用 Decagramma-Ton 提交

本项目的 Git 提交身份固定为：

```text
Decagrammaton <326039646+Decagramma-Ton@users.noreply.github.com>
```

远端仓库为 [Decagramma-Ton/Kivowiki-Android-AinSophAur](https://github.com/Decagramma-Ton/Kivowiki-Android-AinSophAur)。以下操作只设置当前仓库，不会改动电脑上其他仓库的 Git 身份。

## 日常提交

最方便的方式是双击仓库根目录的 `快捷提交到Decagramma-Ton.bat`。脚本会：

1. 检查当前目录和远端地址。
2. 写入本仓库的作者名与 GitHub noreply 邮箱。
3. 展示待提交的文件，并提示输入提交说明。
4. 暂存未被 `.gitignore` 排除的改动；再次确认后提交并推送到 `origin/main`。

提交前请认真查看脚本列出的文件。`.gitignore` 只能排除已知模式，不能替代人工检查；不要把口令、令牌、签名密钥、APK、私有资料或本机日志放进待提交目录。

## 首次在这台电脑授权

快捷脚本首次推送时，Git Credential Manager 可能打开 GitHub 浏览器授权页。请确认登录账号是 `Decagramma-Ton` 并完成 GitHub 授权；不要把密码或个人访问令牌填写进项目文件、BAT 脚本或提交信息。

若浏览器授权没有自动出现，可以在仓库根目录打开 PowerShell，执行：

```powershell
git credential-manager github login --username Decagramma-Ton --browser
```

完成授权后，再运行快捷脚本即可。授权信息由 Windows 的凭据管理器保存，与代码仓库分离。

## 手动提交

如不使用 BAT，可在仓库根目录执行：

```powershell
git config user.name "Decagrammaton"
git config user.email "326039646+Decagramma-Ton@users.noreply.github.com"
git status --short
git add -A
git diff --cached --stat
git commit -m "说明本次修改"
git push -u origin main
```

提交后可用下列命令确认作者、邮箱和远端分支：

```powershell
git log -1 --format="%h %an <%ae> %s"
git status --short --branch
```

正常情况下，第二条命令会显示 `## main...origin/main` 且没有额外文件。
