import { mount } from '@vue/test-utils'
import AsyncStatePanel from './AsyncStatePanel.vue'

describe('AsyncStatePanel', () => {
  it('exposes a labelled loading state without rendering content', () => {
    const wrapper = mount(AsyncStatePanel, {
      props: { loading: true, loadingLabel: '正在加载任务' },
      slots: { default: '<div>任务内容</div>' }
    })

    expect(wrapper.get('[role="status"]').attributes('aria-label')).toBe('正在加载任务')
    expect(wrapper.text()).not.toContain('任务内容')
  })

  it('renders an explicit empty state', () => {
    const wrapper = mount(AsyncStatePanel, {
      props: { empty: true, emptyDescription: '没有符合条件的任务' }
    })

    expect(wrapper.text()).toContain('没有符合条件的任务')
  })

  it('emits retry from the error state', async () => {
    const wrapper = mount(AsyncStatePanel, {
      props: { error: true, errorTitle: '任务读取失败' }
    })

    expect(wrapper.get('[role="alert"]').text()).toContain('任务读取失败')
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
