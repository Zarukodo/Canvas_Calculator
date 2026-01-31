import os

# 設定要忽略的資料夾 (這些對 AI 理解架構沒幫助，且檔案超多)
IGNORE_DIRS = {'.git', '.gradle', '.idea', 'build', 'captures', '.externalNativeBuild'}
# 設定要忽略的檔案類型 (可選)
IGNORE_EXTENSIONS = {'.png', '.jpg', '.jpeg', '.webp', '.jar', '.class', '.ap_'}

def print_tree(startpath, prefix=""):
    # 取得當前目錄下的所有項目並排序
    try:
        items = sorted(os.listdir(startpath))
    except PermissionError:
        return

    # 過濾掉不想看的項目
    items = [i for i in items if i not in IGNORE_DIRS and not i.startswith('.')]
    
    for index, item in enumerate(items):
        path = os.path.join(startpath, item)
        is_last = (index == len(items) - 1)
        
        # 設定顯示符號
        connector = "└── " if is_last else "├── "
        
        # 如果是檔案，檢查副檔名是否要忽略
        if os.path.isfile(path):
            if any(item.endswith(ext) for ext in IGNORE_EXTENSIONS):
                continue
            print(prefix + connector + item)
            
        # 如果是資料夾，遞迴列印
        elif os.path.isdir(path):
            print(prefix + connector + item + "/")
            extension = "    " if is_last else "│   "
            print_tree(path, prefix + extension)

if __name__ == "__main__":
    current_dir = os.getcwd()
    print(f"Project Structure: {os.path.basename(current_dir)}/")
    print_tree(current_dir)
    print("\n[Copy above structure to your AI prompt]")