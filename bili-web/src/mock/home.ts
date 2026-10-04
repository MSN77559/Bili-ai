/**
 * 首页静态常量（ADR-05 / ADR-16）：14 分类 + 渐变预设 + 4 轮播 + 竖栏标签。
 * Phase 2（TD-06 偿还）：视频卡片已切 API-VD02 真实数据，MockCard/CARDS 已删除；
 * 轮播/横幅/竖栏维持 mock（无真实运营位概念，07 范围外）。
 */

/** 14 分类（ADR-10 定稿清单；名称映射 SSOT，index+1 即分区 id） */
export const CATEGORIES: string[] = [
  '动画', '番剧', '国创', '音乐', '舞蹈', '游戏', '知识', '科技',
  '生活', '美食', '鬼畜', '时尚', '娱乐', '影视',
]

/** 预设渐变（卡片封面循环使用，06 §3.1；coverUrl=null 渐变兜底 ADR-17） */
export const GRADIENTS: string[] = [
  'linear-gradient(135deg, #667eea, #764ba2)',
  'linear-gradient(135deg, #f093fb, #f5576c)',
  'linear-gradient(135deg, #4facfe, #00f2fe)',
  'linear-gradient(135deg, #43e97b, #38f9d7)',
  'linear-gradient(135deg, #fa709a, #fee140)',
  'linear-gradient(135deg, #30cfd0, #330867)',
]

/** 竖栏顶部标签（06 §3.1：静态展示，无响应） */
export const SIDEBAR_TAGS: string[] = ['动态', '热门']

/** 竖栏底部功能链接（06 §3.1：静态展示，无响应） */
export const SIDEBAR_LINKS: string[] = ['专栏', '活动', '直播', '课堂']

export interface MockSlide {
  id: number
  title: string
  subtitle: string
  gradient: string
}

/** 轮播 4 张（06 §3.1：4s 自动切换、悬停暂停、箭头/指示点可切） */
export const SLIDES: MockSlide[] = [
  { id: 1, title: '新番速递', subtitle: '本季度最值得追的十部作品', gradient: 'linear-gradient(120deg, #667eea, #764ba2)' },
  { id: 2, title: '知识区精选', subtitle: '硬核科普每周更新', gradient: 'linear-gradient(120deg, #4facfe, #00f2fe)' },
  { id: 3, title: '美食UP主推荐', subtitle: '深夜放毒合集', gradient: 'linear-gradient(120deg, #fa709a, #fee140)' },
  { id: 4, title: '鬼畜镇站之宝', subtitle: '全场名场面回顾', gradient: 'linear-gradient(120deg, #43e97b, #38f9d7)' },
]
